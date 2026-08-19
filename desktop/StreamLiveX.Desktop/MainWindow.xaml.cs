using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Effects;
using System.Windows.Threading;
using LibVLCSharp.Shared;
using Microsoft.Web.WebView2.Core;
using Microsoft.Win32;
using VlcMediaPlayer = LibVLCSharp.Shared.MediaPlayer;

namespace StreamLiveX.Desktop;

public partial class MainWindow : Window
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };

    private LibVLC _libVlc;
    private VlcMediaPlayer _mediaPlayer;
    private readonly Uri _appUri;
    private readonly DispatcherTimer _chromeTimer;
    private readonly HttpClient _httpClient;
    private readonly HashSet<string> _closedPlaybackSessions = new(StringComparer.Ordinal);
    private List<Uri> _playbackCandidates = [];
    private int _playbackCandidateIndex;
    private int _playbackRetryCount;
    private int _playbackGeneration;
    private bool _playbackRecoveryInProgress;
    private Media? _activeMedia;
    private NativePlayMessage? _activeRequest;
    private CancellationTokenSource? _downloadCancellation;
    private CancellationTokenSource? _subtitleLoadCancellation;
    private List<SubtitleCue> _customSubtitleCues = [];
    private string? _activeCustomSubtitleUrl;
    private int _activeSubtitleCueIndex = -1;
    private bool _isSeeking;
    private bool _isFullscreen;
    // Tam ekrana girmeden onceki pencere durumu -- cikinca AYNI boyuta don. Onceden
    // dogrudan WindowState.Normal'a donuluyordu; pencere tam ekrandan once maximize/buyuk
    // ise cikista kucuk "yarim pencere" kaliyordu.
    private WindowState _preFullscreenState = WindowState.Normal;
    private WindowStyle _preFullscreenStyle = WindowStyle.SingleBorderWindow;
    private ResizeMode _preFullscreenResize = ResizeMode.CanResize;
    private System.Windows.Threading.DispatcherTimer? _focusReassertTimer;
    private bool _videoClickArmed;
    private bool _nativePlayerOpen;
    private bool _updatingPlayerOptions;
    private long _pendingResumeTime;
    private long _lastProgressNotification;
    private bool _pauseAfterResume;
    private int _appliedSubtitleSize = 100;
    private string _appliedSubtitleColor = "#ffffff";
    private string _appliedSubtitleBackground = "shadow";

    public MainWindow()
    {
        InitializeComponent();

        _appUri = ResolveAppUri();
        _httpClient = new HttpClient(new HttpClientHandler { AllowAutoRedirect = true });
        _httpClient.DefaultRequestHeaders.UserAgent.ParseAdd("VLC/3.0 StreamLiveX/1.0");
        _libVlc = CreateLibVlc(new NativePlayerPreferences());
        _mediaPlayer = new VlcMediaPlayer(_libVlc);
        VideoSurface.MediaPlayer = _mediaPlayer;
        _updatingPlayerOptions = true;
        PlaybackRates.ItemsSource = new[]
        {
            new RateOption(0.5f, "0.5x"), new RateOption(0.75f, "0.75x"),
            new RateOption(1f, "1x"), new RateOption(1.25f, "1.25x"),
            new RateOption(1.5f, "1.5x"), new RateOption(2f, "2x")
        };
        SubtitleSizes.ItemsSource = new[]
        {
            new SubtitleSizeOption(70, "%70"), new SubtitleSizeOption(85, "%85"),
            new SubtitleSizeOption(100, "%100"), new SubtitleSizeOption(120, "%120"),
            new SubtitleSizeOption(140, "%140"), new SubtitleSizeOption(160, "%160")
        };
        SubtitleColors.ItemsSource = new[]
        {
            new SubtitleColorOption("#ffffff", "Beyaz"),
            new SubtitleColorOption("#ffd84d", "Sarı"),
            new SubtitleColorOption("#67e8f9", "Mavi"),
            new SubtitleColorOption("#86efac", "Yeşil")
        };
        PlaybackRates.SelectedIndex = 2;
        SubtitleSizes.SelectedIndex = 2;
        SubtitleColors.SelectedIndex = 0;
        _updatingPlayerOptions = false;
        _chromeTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(3) };
        _chromeTimer.Tick += (_, _) =>
        {
            if (_mediaPlayer.IsPlaying && !PlayerChrome.IsMouseOver && SpeedSettingsButton.IsChecked != true && MediaSettingsButton.IsChecked != true)
            {
                PlayerChrome.Visibility = Visibility.Collapsed;
                NativePlayer.Cursor = Cursors.None;
                _videoClickArmed = false;
                _chromeTimer.Stop();
            }
        };

        AttachMediaPlayerEvents(_mediaPlayer);

        Loaded += async (_, _) => await InitializeBrowserAsync();
        Closing += (_, _) => DisposePlayer();
    }

    private static LibVLC CreateLibVlc(NativePlayerPreferences? preferences)
    {
        var requestedSize = Math.Clamp(preferences?.SubtitleSize ?? 100, 70, 180);
        var relativeSize = Math.Clamp((int)Math.Round(16d * 100d / requestedSize), 8, 24);
        var color = NormalizeSubtitleColor(preferences?.SubtitleColor);
        _ = int.TryParse(color.AsSpan(1), System.Globalization.NumberStyles.HexNumber, System.Globalization.CultureInfo.InvariantCulture, out var rgb);
        var background = preferences?.SubtitleBackground ?? "shadow";
        var boxed = string.Equals(background, "box", StringComparison.OrdinalIgnoreCase);
        var shadowed = string.Equals(background, "shadow", StringComparison.OrdinalIgnoreCase);

        return new LibVLC(
            "--no-video-title-show",
            "--network-caching=1500",
            "--http-reconnect",
            $"--freetype-rel-fontsize={relativeSize}",
            $"--sub-text-scale={requestedSize}",
            $"--freetype-color={rgb}",
            "--freetype-opacity=255",
            "--no-subsdec-formatted",
            $"--freetype-background-opacity={(boxed ? 190 : 0)}",
            $"--freetype-shadow-opacity={(shadowed ? 128 : 0)}",
            "--freetype-outline-opacity=255");
    }

    private static string NormalizeSubtitleColor(string? value)
    {
        var candidate = value?.Trim().ToLowerInvariant();
        if (candidate?.Length == 7 && candidate[0] == '#' && candidate.Skip(1).All(Uri.IsHexDigit))
        {
            return candidate;
        }
        return "#ffffff";
    }

    private void AttachMediaPlayerEvents(VlcMediaPlayer player)
    {
        player.Playing += (_, args) => MediaPlayer_Playing(player, args);
        player.Paused += (_, _) => Dispatcher.InvokeAsync(() =>
        {
            if (!ReferenceEquals(player, _mediaPlayer))
            {
                return;
            }
            PlayPauseButton.Content = "▶";
            PlaybackStatus.Text = "Duraklatıldı";
            ShowPlayerChrome(false);
        });
        player.Stopped += (_, _) => Dispatcher.InvokeAsync(() =>
        {
            if (ReferenceEquals(player, _mediaPlayer))
            {
                PlayPauseButton.Content = "▶";
            }
        });
        player.EncounteredError += (_, _) => Dispatcher.InvokeAsync(() =>
        {
            if (ReferenceEquals(player, _mediaPlayer))
            {
                _ = RecoverLivePlaybackAsync(player);
            }
        });
        player.EndReached += (_, _) => Dispatcher.InvokeAsync(() =>
        {
            if (ReferenceEquals(player, _mediaPlayer))
            {
                CloseNativePlayer();
            }
        });
        player.TimeChanged += (_, args) => Dispatcher.InvokeAsync(() =>
        {
            if (ReferenceEquals(player, _mediaPlayer))
            {
                UpdateTime(args.Time);
                var length = player.Length;
                if (_activeRequest?.Item is not null &&
                    !string.Equals(_activeRequest.Item.Kind, "live", StringComparison.OrdinalIgnoreCase) &&
                    length > 0 && args.Time >= 5_000 &&
                    (args.Time - _lastProgressNotification >= 5_000 || args.Time < _lastProgressNotification))
                {
                    _lastProgressNotification = args.Time;
                    SendWebEvent("streamlivex:native-player-progress", new
                    {
                        current = args.Time / 1000d,
                        duration = length / 1000d
                    });
                }
            }
        });
        player.LengthChanged += (_, args) => Dispatcher.InvokeAsync(() =>
        {
            if (ReferenceEquals(player, _mediaPlayer))
            {
                PositionSlider.Maximum = Math.Max(1, args.Length);
            }
        });
    }

    private static Uri ResolveAppUri()
    {
        var configured = Environment.GetEnvironmentVariable("STREAMLIVEX_WEB_URL")?.Trim();
        if (Uri.TryCreate(configured, UriKind.Absolute, out var candidate) &&
            (candidate.Scheme == Uri.UriSchemeHttp || candidate.Scheme == Uri.UriSchemeHttps))
        {
            return candidate;
        }

        return new Uri("https://streamlivex.com/app");
    }

    private async Task InitializeBrowserAsync()
    {
        try
        {
            BrowserError.Visibility = Visibility.Collapsed;
            await Browser.EnsureCoreWebView2Async();

            // Uzak web arayüzü sık güncelleniyor; WebView2 HTML belgesini ve varlıkları önbelleğe
            // alıp yeni CSS/JS deploy edilse bile eski arayüzü göstermeye devam edebiliyordu. Her
            // açılışta disk önbelleğini temizleyerek kullanıcının daima en güncel arayüzü görmesini
            // sağlıyoruz (varlıklar içerik-hash'li olduğundan bu yalnızca güncelleme olduğunda fark yaratır).
            await Browser.CoreWebView2.Profile.ClearBrowsingDataAsync(CoreWebView2BrowsingDataKinds.DiskCache);

            Browser.CoreWebView2.Settings.AreDevToolsEnabled = _appUri.IsLoopback;
            Browser.CoreWebView2.Settings.AreDefaultContextMenusEnabled = false;
            Browser.CoreWebView2.Settings.IsStatusBarEnabled = false;
            Browser.CoreWebView2.WebMessageReceived -= Browser_WebMessageReceived;
            Browser.CoreWebView2.WebMessageReceived += Browser_WebMessageReceived;
            Browser.CoreWebView2.NavigationStarting -= Browser_NavigationStarting;
            Browser.CoreWebView2.NavigationStarting += Browser_NavigationStarting;
            Browser.CoreWebView2.NewWindowRequested -= Browser_NewWindowRequested;
            Browser.CoreWebView2.NewWindowRequested += Browser_NewWindowRequested;
            Browser.Source = _appUri;
        }
        catch (Exception ex)
        {
            BrowserErrorText.Text = $"Web arayüzüne bağlanılamadı. İnternet bağlantısını veya STREAMLIVEX_WEB_URL ayarını kontrol edin.\n\n{ex.Message}";
            BrowserError.Visibility = Visibility.Visible;
        }
    }

    private void Browser_NavigationStarting(object? sender, CoreWebView2NavigationStartingEventArgs args)
    {
        if (!Uri.TryCreate(args.Uri, UriKind.Absolute, out var destination) ||
            (destination.Scheme != Uri.UriSchemeHttp && destination.Scheme != Uri.UriSchemeHttps))
        {
            args.Cancel = true;
        }
    }

    private static void Browser_NewWindowRequested(object? sender, CoreWebView2NewWindowRequestedEventArgs args)
    {
        args.Handled = true;
        if (Uri.TryCreate(args.Uri, UriKind.Absolute, out var uri) &&
            (uri.Scheme == Uri.UriSchemeHttp || uri.Scheme == Uri.UriSchemeHttps))
        {
            Process.Start(new ProcessStartInfo(uri.AbsoluteUri) { UseShellExecute = true });
        }
    }

    private void Browser_WebMessageReceived(object? sender, CoreWebView2WebMessageReceivedEventArgs args)
    {
        try
        {
            var message = JsonSerializer.Deserialize<NativePlayMessage>(args.WebMessageAsJson, JsonOptions);
            if (message is null)
            {
                return;
            }

            if (string.Equals(message.Type, "close", StringComparison.OrdinalIgnoreCase))
            {
                if (!string.IsNullOrWhiteSpace(message.SessionId) &&
                    !string.Equals(message.SessionId, _activeRequest?.SessionId, StringComparison.Ordinal))
                {
                    return;
                }
                CloseNativePlayer(false);
                return;
            }

            if (!string.Equals(message.Type, "play", StringComparison.OrdinalIgnoreCase) || message.Item is null)
            {
                return;
            }

            if (!string.IsNullOrWhiteSpace(message.SessionId) && _closedPlaybackSessions.Contains(message.SessionId))
            {
                return;
            }

            if (!Uri.TryCreate(message.Item.Url, UriKind.Absolute, out var mediaUri) ||
                (mediaUri.Scheme != Uri.UriSchemeHttp && mediaUri.Scheme != Uri.UriSchemeHttps))
            {
                SendWebEvent("streamlivex:native-player-error", "Yalnızca HTTP veya HTTPS yayınları açılabilir.");
                return;
            }

            StartNativePlayer(message, mediaUri, message.ResumeTime ?? 0);
        }
        catch (JsonException)
        {
            SendWebEvent("streamlivex:native-player-error", "Oynatma bilgisi okunamadı.");
        }
    }

    private void StartNativePlayer(NativePlayMessage request, Uri mediaUri, long resumeTime = 0, bool pauseAfterResume = false, bool keepSettingsPanelOpen = false)
    {
        EnsureSubtitleEngine(request.Preferences);
        _activeRequest = request;
        _playbackCandidates = BuildPlaybackCandidates(request.Item, mediaUri);
        _playbackCandidateIndex = 0;
        _playbackRetryCount = 0;
        _playbackGeneration++;
        _playbackRecoveryInProgress = false;
        _pendingResumeTime = Math.Max(0, resumeTime);
        _lastProgressNotification = 0;
        _pauseAfterResume = pauseAfterResume;
        var activeMedia = OpenActiveMedia(mediaUri, request);

        ClearCustomSubtitles();

        MediaTitle.Text = request.Item?.Name ?? "StreamLiveX";
        // "Sonraki bölüm" butonu yalnizca web tarafi sirada baska bir bolum oldugunu bildirdiginde.
        NextEpisodeButton.Visibility = request.Item?.HasNext == true ? Visibility.Visible : Visibility.Collapsed;
        PlaybackStatus.Text = "Yayın hazırlanıyor";
        PositionSlider.Value = 0;
        PositionSlider.IsEnabled = !string.Equals(request.Item?.Kind, "live", StringComparison.OrdinalIgnoreCase);
        TimeText.Text = PositionSlider.IsEnabled ? "00:00 / 00:00" : "CANLI";
        AudioTracks.ItemsSource = null;
        SubtitleTracks.ItemsSource = null;
        SpeedSettingsButton.IsChecked = false;
        SpeedSettingsPanel.Visibility = Visibility.Collapsed;
        MediaSettingsButton.IsChecked = keepSettingsPanelOpen;
        MediaSettingsPanel.Visibility = keepSettingsPanelOpen ? Visibility.Visible : Visibility.Collapsed;
        ConfigurePlayerOptions(request);
        var isLive = string.Equals(request.Item?.Kind, "live", StringComparison.OrdinalIgnoreCase);
        PlaybackRates.IsEnabled = !isLive;
        DownloadButton.IsEnabled = !isLive || _downloadCancellation is not null;
        if (_downloadCancellation is null)
        {
            DownloadButton.Content = "⇩";
        }
        Browser.Visibility = Visibility.Collapsed;
        BrowserError.Visibility = Visibility.Collapsed;
        if (VideoSurface.Content is null)
        {
            VideoSurface.Content = VideoOverlay;
        }
        VideoSurface.MediaPlayer = _mediaPlayer;
        VideoSurface.Visibility = Visibility.Visible;
        NativePlayer.Visibility = Visibility.Visible;
        SetVideoForegroundVisible(true);
        _nativePlayerOpen = true;
        _videoClickArmed = false;
        ShowPlayerChrome(false);
        _mediaPlayer.Play(activeMedia);
        _ = MonitorDurationAsync(_mediaPlayer, activeMedia);
        // Klavye odağını WPF penceresine ver ki ok tuşları (ileri/geri sarma) ve Escape
        // (tam ekrandan çıkış) çalışsın. VLC video yüzeyi (airspace HWND) odağı kapabildiği için
        // TEK seferlik odaklamak yetmiyordu -- kullanici once ekrana tiklamadan tuslar
        // calismiyordu. Play/buffering sirasinda odagi birkac kez yeniden vererek bunu asiyoruz.
        StartFocusReassert();
    }

    private void FocusNativePlayer()
    {
        if (!_nativePlayerOpen)
        {
            return;
        }
        Activate();
        NativePlayer.Focus();
        Keyboard.Focus(NativePlayer);
    }

    // VLC video HWND'i, oynatma baslarken odagi birkac kez geri kapabiliyor. Odagi hemen ve
    // ardindan kisa araliklarla birkac kez daha WPF penceresine geri veriyoruz; kullanici
    // (Play tamamlaninca) odagi ekrana tiklamadan da Esc/yon tuslarini kullanabilsin.
    private void StartFocusReassert()
    {
        FocusNativePlayer();
        _focusReassertTimer?.Stop();
        _focusReassertTimer = new System.Windows.Threading.DispatcherTimer
        {
            Interval = TimeSpan.FromMilliseconds(250),
        };
        var ticks = 0;
        _focusReassertTimer.Tick += (_, _) =>
        {
            ticks++;
            // Odak zaten WPF penceresindeyse (kullanici baska yere gitmediyse) israr etmeye gerek yok.
            var focusOnPlayer = _nativePlayerOpen && (NativePlayer.IsKeyboardFocusWithin || IsKeyboardFocusWithin);
            if (!_nativePlayerOpen || focusOnPlayer || ticks >= 10)
            {
                _focusReassertTimer?.Stop();
                _focusReassertTimer = null;
                return;
            }
            FocusNativePlayer();
        };
        _focusReassertTimer.Start();
    }

    private static List<Uri> BuildPlaybackCandidates(NativeMediaItem? item, Uri original)
    {
        var candidates = new List<Uri> { original };
        if (string.Equals(item?.Kind, "live", StringComparison.OrdinalIgnoreCase) &&
            original.AbsolutePath.EndsWith(".ts", StringComparison.OrdinalIgnoreCase))
        {
            var builder = new UriBuilder(original)
            {
                Path = Regex.Replace(original.AbsolutePath, "\\.ts$", ".m3u8", RegexOptions.IgnoreCase)
            };
            candidates.Add(builder.Uri);
        }
        return candidates.Distinct().ToList();
    }

    private Media OpenActiveMedia(Uri mediaUri, NativePlayMessage request)
    {
        _mediaPlayer.Stop();
        _activeMedia?.Dispose();
        _activeMedia = new Media(_libVlc, mediaUri);
        _activeMedia.AddOption(":network-caching=1500");
        _activeMedia.AddOption(":http-reconnect=true");
        _activeMedia.AddOption(":sub-margin=88");
        _activeMedia.AddOption($":sub-text-scale={Math.Clamp(request.Preferences?.SubtitleSize ?? 100, 70, 180)}");
        if (string.Equals(request.Item?.Kind, "live", StringComparison.OrdinalIgnoreCase))
        {
            _activeMedia.AddOption(":live-caching=1500");
        }
        return _activeMedia;
    }

    private async Task RecoverLivePlaybackAsync(VlcMediaPlayer player)
    {
        if (_playbackRecoveryInProgress || !ReferenceEquals(player, _mediaPlayer) || !_nativePlayerOpen ||
            !string.Equals(_activeRequest?.Item?.Kind, "live", StringComparison.OrdinalIgnoreCase))
        {
            PlaybackStatus.Text = "Yayın oynatılamadı";
            return;
        }

        var request = _activeRequest;
        if (request is null || _playbackCandidates.Count == 0)
        {
            PlaybackStatus.Text = "Yayın oynatılamadı";
            return;
        }

        if (_playbackRetryCount < 1)
        {
            _playbackRetryCount++;
        }
        else if (_playbackCandidateIndex + 1 < _playbackCandidates.Count)
        {
            _playbackCandidateIndex++;
            _playbackRetryCount = 0;
        }
        else
        {
            PlaybackStatus.Text = "Yayın oynatılamadı";
            SendWebEvent("streamlivex:native-player-error", "Yayın kaynağı geçici olarak kullanılamıyor.");
            return;
        }

        _playbackRecoveryInProgress = true;
        var generation = _playbackGeneration;
        PlaybackStatus.Text = "Yayın yeniden deneniyor";
        try
        {
            _mediaPlayer.Stop();
            await Task.Delay(750);
            if (generation != _playbackGeneration || !ReferenceEquals(player, _mediaPlayer) || !_nativePlayerOpen) return;
            var activeMedia = OpenActiveMedia(_playbackCandidates[_playbackCandidateIndex], request);
            _mediaPlayer.Play(activeMedia);
        }
        finally
        {
            _playbackRecoveryInProgress = false;
        }
    }

    private void ConfigurePlayerOptions(NativePlayMessage request)
    {
        _updatingPlayerOptions = true;
        var preferences = request.Preferences ??= new NativePlayerPreferences();
        var rates = PlaybackRates.ItemsSource.Cast<RateOption>().ToList();
        PlaybackRates.SelectedItem = rates.OrderBy(option => Math.Abs(option.Value - (preferences.PlaybackRate ?? 1f))).First();
        var sizes = SubtitleSizes.ItemsSource.Cast<SubtitleSizeOption>().ToList();
        SubtitleSizes.SelectedItem = sizes.OrderBy(option => Math.Abs(option.Value - (preferences.SubtitleSize ?? 100))).First();
        var colors = SubtitleColors.ItemsSource.Cast<SubtitleColorOption>().ToList();
        SubtitleColors.SelectedItem = colors.FirstOrDefault(option => string.Equals(option.Value, preferences.SubtitleColor, StringComparison.OrdinalIgnoreCase)) ?? colors[0];
        _updatingPlayerOptions = false;
    }

    private void EnsureSubtitleEngine(NativePlayerPreferences? preferences)
    {
        var requestedSize = Math.Clamp(preferences?.SubtitleSize ?? 100, 70, 180);
        var requestedColor = NormalizeSubtitleColor(preferences?.SubtitleColor);
        var requestedBackground = preferences?.SubtitleBackground ?? "shadow";
        if (requestedSize == _appliedSubtitleSize &&
            string.Equals(requestedColor, _appliedSubtitleColor, StringComparison.OrdinalIgnoreCase) &&
            string.Equals(requestedBackground, _appliedSubtitleBackground, StringComparison.OrdinalIgnoreCase))
        {
            return;
        }

        VideoSurface.MediaPlayer = null;
        var previousPlayer = _mediaPlayer;
        var previousLibVlc = _libVlc;
        _libVlc = CreateLibVlc(preferences);
        _mediaPlayer = new VlcMediaPlayer(_libVlc);
        AttachMediaPlayerEvents(_mediaPlayer);
        _appliedSubtitleSize = requestedSize;
        _appliedSubtitleColor = requestedColor;
        _appliedSubtitleBackground = requestedBackground;
        previousPlayer.Dispose();
        previousLibVlc.Dispose();
    }

    public void ActivateFromProtocol(string activation)
    {
        if (!Uri.TryCreate(activation, UriKind.Absolute, out var protocolUri) ||
            protocolUri.Scheme != "streamlivex" ||
            !string.Equals(protocolUri.Host, "play", StringComparison.OrdinalIgnoreCase))
        {
            return;
        }

        var parameters = protocolUri.Query
            .TrimStart('?')
            .Split('&', StringSplitOptions.RemoveEmptyEntries)
            .Select(part => part.Split('=', 2))
            .Where(pair => pair.Length == 2)
            .ToDictionary(pair => WebUtility.UrlDecode(pair[0]), pair => WebUtility.UrlDecode(pair[1]), StringComparer.OrdinalIgnoreCase);

        if (!parameters.TryGetValue("url", out var source) ||
            !Uri.TryCreate(source, UriKind.Absolute, out var mediaUri) ||
            (mediaUri.Scheme != Uri.UriSchemeHttp && mediaUri.Scheme != Uri.UriSchemeHttps))
        {
            return;
        }

        var request = new NativePlayMessage
        {
            Type = "play",
            Item = new NativeMediaItem
            {
                Name = parameters.GetValueOrDefault("title") ?? "StreamLiveX",
                Url = mediaUri.AbsoluteUri,
                Kind = parameters.GetValueOrDefault("kind") ?? "movie"
            }
        };
        StartNativePlayer(request, mediaUri);
    }

    private async void MediaPlayer_Playing(VlcMediaPlayer player, EventArgs args)
    {
        if (!ReferenceEquals(player, _mediaPlayer))
        {
            return;
        }
        await Dispatcher.InvokeAsync(() =>
        {
            _playbackRetryCount = 0;
            PlaybackStatus.Text = "Oynatılıyor · VLC";
            PlayPauseButton.Content = "⏸";
            var rate = _activeRequest?.Preferences?.PlaybackRate ?? 1f;
            if (!string.Equals(_activeRequest?.Item?.Kind, "live", StringComparison.OrdinalIgnoreCase))
            {
                _mediaPlayer.SetRate(rate);
            }
            if (_pendingResumeTime > 0)
            {
                _mediaPlayer.Time = _mediaPlayer.Length > 0 ? Math.Min(_pendingResumeTime, _mediaPlayer.Length) : _pendingResumeTime;
                _pendingResumeTime = 0;
            }
            if (_pauseAfterResume)
            {
                _pauseAfterResume = false;
                _mediaPlayer.Pause();
            }
            var length = Math.Max(_mediaPlayer.Length, _activeMedia?.Duration ?? 0);
            if (PositionSlider.IsEnabled && length > 0)
            {
                PositionSlider.Maximum = length;
                UpdateTime(Math.Max(0, _mediaPlayer.Time));
            }
            ShowPlayerChrome();
        });

        await Task.Delay(700);
        if (ReferenceEquals(player, _mediaPlayer))
        {
            await Dispatcher.InvokeAsync(RefreshTracks);
        }
    }

    private async Task MonitorDurationAsync(VlcMediaPlayer player, Media media)
    {
        for (var attempt = 0; attempt < 40; attempt++)
        {
            await Task.Delay(250);
            if (!ReferenceEquals(player, _mediaPlayer) || !ReferenceEquals(media, _activeMedia) || !_nativePlayerOpen)
            {
                return;
            }
            var length = Math.Max(player.Length, media.Duration);
            if (length <= 0)
            {
                continue;
            }
            await Dispatcher.InvokeAsync(() =>
            {
                if (ReferenceEquals(player, _mediaPlayer) && PositionSlider.IsEnabled)
                {
                    PositionSlider.Maximum = length;
                    UpdateTime(Math.Max(0, player.Time));
                }
            });
            return;
        }
    }

    private void RefreshTracks()
    {
        var audio = (_mediaPlayer.AudioTrackDescription ?? [])
            .Select(track => new TrackOption(track.Id, track.Name ?? $"Ses {track.Id}"))
            .ToList();
        AudioTracks.ItemsSource = audio;
        var selectedAudio = audio.FirstOrDefault(track => track.Id == _mediaPlayer.AudioTrack) ?? audio.FirstOrDefault();

        var subtitles = (_mediaPlayer.SpuDescription ?? [])
            .Select(track => new TrackOption(track.Id, track.Id == -1 ? "Kapalı" : track.Name ?? $"Altyazı {track.Id}"))
            .ToList();
        if (subtitles.All(track => track.Id != -1))
        {
            subtitles.Insert(0, new TrackOption(-1, "Kapalı"));
        }
        var externalSubtitles = (_activeRequest?.Item?.Subtitles ?? [])
            .Where(track => Uri.TryCreate(track.Src, UriKind.Absolute, out var uri) &&
                            (uri.Scheme == Uri.UriSchemeHttp || uri.Scheme == Uri.UriSchemeHttps))
            .Select((track, index) => new TrackOption(
                -1000 - index,
                string.IsNullOrWhiteSpace(track.Label) ? $"Harici altyazı {index + 1}" : track.Label,
                track.Src,
                track.Language))
            .ToList();
        subtitles.AddRange(externalSubtitles);
        SubtitleTracks.ItemsSource = subtitles;

        var subtitleMode = _activeRequest?.Preferences?.SubtitleMode;
        var subtitleLanguage = _activeRequest?.Preferences?.SubtitleLanguage;
        var selectedSubtitle = string.Equals(subtitleMode, "off", StringComparison.OrdinalIgnoreCase)
            ? subtitles.FirstOrDefault(track => track.Id == -1)
            : subtitles.FirstOrDefault(track => track.Id != -1 &&
                !string.IsNullOrWhiteSpace(subtitleLanguage) &&
                subtitleLanguage != "auto" &&
                (TrackMatchesLanguage(track.Name, subtitleLanguage) ||
                 (!string.IsNullOrWhiteSpace(track.Language) && track.Language.StartsWith(subtitleLanguage, StringComparison.OrdinalIgnoreCase))))
              ?? externalSubtitles.FirstOrDefault()
              ?? subtitles.FirstOrDefault(track => track.Id >= 0)
              ?? subtitles.FirstOrDefault();

        var language = _activeRequest?.Preferences?.AudioLanguage;
        if (!string.IsNullOrWhiteSpace(language) && language is not "auto" and not "original")
        {
            var preferred = audio.FirstOrDefault(track => track.Name.Contains(language, StringComparison.OrdinalIgnoreCase));
            if (preferred is not null)
            {
                selectedAudio = preferred;
            }
        }

        AudioTracks.SelectedItem = selectedAudio;
        if (selectedAudio is not null)
        {
            _mediaPlayer.SetAudioTrack(selectedAudio.Id);
        }
        SubtitleTracks.SelectedItem = selectedSubtitle;

    }

    private static bool TrackMatchesLanguage(string trackName, string language)
    {
        var aliases = language.ToLowerInvariant() switch
        {
            "tr" => new[] { "turkish", "türkçe", "turkce" },
            "en" => new[] { "english", "ingilizce", "i̇ngilizce" },
            "de" => new[] { "german", "deutsch", "almanca" },
            "fr" => new[] { "french", "français", "francais" },
            "es" => new[] { "spanish", "español", "espanol" },
            "ar" => new[] { "arabic", "العربية" },
            _ => new[] { language }
        };
        return aliases.Any(alias => trackName.Contains(alias, StringComparison.OrdinalIgnoreCase)) ||
               trackName.Equals(language, StringComparison.OrdinalIgnoreCase) ||
               trackName.Contains($"[{language}]", StringComparison.OrdinalIgnoreCase) ||
               trackName.StartsWith($"{language} ", StringComparison.OrdinalIgnoreCase);
    }

    private void UpdateTime(long current)
    {
        UpdateCustomSubtitle(current);
        var length = Math.Max(_mediaPlayer.Length, _activeMedia?.Duration ?? 0);
        if (PositionSlider.IsEnabled && length > 0)
        {
            PositionSlider.Maximum = length;
        }
        if (!_isSeeking && PositionSlider.IsEnabled)
        {
            PositionSlider.Value = Math.Clamp(current, PositionSlider.Minimum, PositionSlider.Maximum);
        }

        if (PositionSlider.IsEnabled)
        {
            TimeText.Text = $"{FormatTime(current)} / {FormatTime(length)}";
        }
    }

    private static string FormatTime(long milliseconds)
    {
        var value = TimeSpan.FromMilliseconds(Math.Max(0, milliseconds));
        return value.TotalHours >= 1 ? value.ToString(@"hh\:mm\:ss") : value.ToString(@"mm\:ss");
    }

    private void PlayPause_Click(object sender, RoutedEventArgs e)
    {
        TogglePlayback();
    }

    private void TogglePlayback()
    {
        if (_mediaPlayer.IsPlaying)
        {
            _mediaPlayer.Pause();
        }
        else
        {
            _mediaPlayer.Play();
        }
    }

    private void ClosePlayer_Click(object sender, RoutedEventArgs e) => CloseNativePlayer();

    private void CloseNativePlayer(bool notifyWeb = true)
    {
        var wasOpen = _nativePlayerOpen;
        var closedSessionId = _activeRequest?.SessionId;
        var currentSeconds = _mediaPlayer.Time / 1000d;
        var durationSeconds = _mediaPlayer.Length / 1000d;
        if (!string.IsNullOrWhiteSpace(closedSessionId))
        {
            _closedPlaybackSessions.Add(closedSessionId);
            if (_closedPlaybackSessions.Count > 64)
            {
                _closedPlaybackSessions.Remove(_closedPlaybackSessions.First());
            }
        }
        _nativePlayerOpen = false;
        _playbackGeneration++;
        _playbackRecoveryInProgress = false;
        _chromeTimer.Stop();
        _mediaPlayer.Stop();
        ClearCustomSubtitles();
        _videoClickArmed = false;
        SpeedSettingsButton.IsChecked = false;
        MediaSettingsButton.IsChecked = false;
        SpeedSettingsPanel.Visibility = Visibility.Collapsed;
        MediaSettingsPanel.Visibility = Visibility.Collapsed;
        PlayerChrome.Visibility = Visibility.Collapsed;
        HideVideoSurface();
        NativePlayer.Visibility = Visibility.Collapsed;
        NativePlayer.Cursor = Cursors.Arrow;
        _activeMedia?.Dispose();
        _activeMedia = null;
        _activeRequest = null;
        Browser.Visibility = Visibility.Visible;
        Browser.Focus();
        if (notifyWeb && wasOpen)
        {
            SendWebEvent("streamlivex:native-player-closed", new
            {
                current = currentSeconds,
                duration = durationSeconds
            });
        }
    }

    private void HideVideoSurface()
    {
        SetVideoForegroundVisible(false);
        VideoSurface.Visibility = Visibility.Collapsed;
        VideoSurface.MediaPlayer = null;
    }

    private void SetVideoForegroundVisible(bool visible)
    {
        try
        {
            var property = VideoSurface.GetType().GetProperty(
                "ForegroundWindow",
                System.Reflection.BindingFlags.Instance | System.Reflection.BindingFlags.Public | System.Reflection.BindingFlags.NonPublic);
            if (property?.GetValue(VideoSurface) is Window foreground)
            {
                if (visible)
                {
                    foreground.Show();
                }
                else
                {
                    foreground.Hide();
                }
            }
        }
        catch
        {
        }
    }

    private void ShowPlayerChrome(bool scheduleHide = true)
    {
        PlayerChrome.Visibility = Visibility.Visible;
        NativePlayer.Cursor = Cursors.Arrow;
        _chromeTimer.Stop();
        if (scheduleHide && _mediaPlayer.IsPlaying)
        {
            _chromeTimer.Start();
        }
    }

    private void NativePlayer_MouseMove(object sender, MouseEventArgs e) => ShowPlayerChrome();

    private void VideoClickSurface_MouseLeftButtonUp(object sender, MouseButtonEventArgs e)
    {
        // Video yüzeyine tıklayınca klavye odağını WPF'te tut ki ok tuşları/Escape çalışmaya devam etsin.
        NativePlayer.Focus();
        ShowPlayerChrome();
        if (!_videoClickArmed)
        {
            _videoClickArmed = true;
            e.Handled = true;
            return;
        }

        TogglePlayback();
        e.Handled = true;
    }

    private void NativePlayer_MouseLeave(object sender, MouseEventArgs e)
    {
        if (_mediaPlayer.IsPlaying)
        {
            _chromeTimer.Stop();
            _chromeTimer.Start();
        }
    }

    private void SpeedSettingsButton_Checked(object sender, RoutedEventArgs e)
    {
        MediaSettingsButton.IsChecked = false;
        MediaSettingsPanel.Visibility = Visibility.Collapsed;
        SpeedSettingsPanel.Visibility = Visibility.Visible;
        ShowPlayerChrome(false);
    }

    private void SpeedSettingsButton_Unchecked(object sender, RoutedEventArgs e)
    {
        SpeedSettingsPanel.Visibility = Visibility.Collapsed;
    }

    private void MediaSettingsButton_Checked(object sender, RoutedEventArgs e)
    {
        SpeedSettingsButton.IsChecked = false;
        SpeedSettingsPanel.Visibility = Visibility.Collapsed;
        MediaSettingsPanel.Visibility = Visibility.Visible;
        ShowPlayerChrome(false);
    }

    private void MediaSettingsButton_Unchecked(object sender, RoutedEventArgs e)
    {
        MediaSettingsPanel.Visibility = Visibility.Collapsed;
    }

    private void PositionSlider_PreviewMouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (!PositionSlider.IsEnabled)
        {
            return;
        }

        _isSeeking = true;
        // Mouse'u yakala ki hizli/tek tikta MouseMove ve MouseUp olaylari kesinlikle bu slider'a
        // ulassin (onceden capture yoktu; ilk tikin "up"i kacip seek islenmiyordu -- kullanici
        // "2-3 kere basmam gerekiyor" diyordu).
        PositionSlider.CaptureMouse();
        UpdateSeekPosition(e.GetPosition(PositionSlider).X);
        // Tek tik: basar basmaz oynatici zamanini da uygula ki ilk tikta aninda ilerlesin.
        CommitSeek();
    }

    private void PositionSlider_PreviewMouseMove(object sender, MouseEventArgs e)
    {
        if (_isSeeking && e.LeftButton == MouseButtonState.Pressed && PositionSlider.IsEnabled)
        {
            UpdateSeekPosition(e.GetPosition(PositionSlider).X);
        }
    }

    private void CommitSeek()
    {
        if (PositionSlider.IsEnabled)
        {
            _mediaPlayer.Time = (long)PositionSlider.Value;
        }
    }

    private void UpdateSeekPosition(double pointerX)
    {
        if (PositionSlider.ActualWidth <= 0)
        {
            return;
        }

        var ratio = Math.Clamp(pointerX / PositionSlider.ActualWidth, 0, 1);
        PositionSlider.Value = PositionSlider.Minimum + (PositionSlider.Maximum - PositionSlider.Minimum) * ratio;
        TimeText.Text = $"{FormatTime((long)PositionSlider.Value)} / {FormatTime(_mediaPlayer.Length)}";
    }

    private void PositionSlider_PreviewMouseLeftButtonUp(object sender, MouseButtonEventArgs e)
    {
        CommitSeek();
        if (PositionSlider.IsMouseCaptured)
        {
            PositionSlider.ReleaseMouseCapture();
        }
        _isSeeking = false;
    }

    private void VolumeSlider_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (_mediaPlayer is not null)
        {
            _mediaPlayer.Volume = (int)e.NewValue;
        }
    }

    private void AudioTracks_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (AudioTracks.SelectedItem is TrackOption track)
        {
            _mediaPlayer.SetAudioTrack(track.Id);
            if (_activeRequest is not null)
            {
                var preferences = _activeRequest.Preferences ??= new NativePlayerPreferences();
                preferences.AudioLanguage = InferLanguagePreference(track.Name, preferences.AudioLanguage);
                SendPlaybackPreferences();
            }
        }
    }

    private async void SubtitleTracks_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (SubtitleTracks.SelectedItem is TrackOption track)
        {
            await ApplySubtitleTrackAsync(track);
            if (_activeRequest is not null)
            {
                var preferences = _activeRequest.Preferences ??= new NativePlayerPreferences();
                preferences.SubtitleMode = track.Id == -1 ? "off" : "on";
                if (track.Id != -1)
                {
                    preferences.SubtitleLanguage = InferLanguagePreference(track.Name, preferences.SubtitleLanguage);
                }
                SendPlaybackPreferences();
            }
        }
    }

    private async Task ApplySubtitleTrackAsync(TrackOption track)
    {
        _subtitleLoadCancellation?.Cancel();
        _subtitleLoadCancellation?.Dispose();
        _subtitleLoadCancellation = null;

        if (string.IsNullOrWhiteSpace(track.ExternalUrl))
        {
            _activeCustomSubtitleUrl = null;
            _customSubtitleCues = [];
            _activeSubtitleCueIndex = -1;
            CustomSubtitleText.Text = "";
            CustomSubtitleContainer.Visibility = Visibility.Collapsed;
            _mediaPlayer.SetSpu(track.Id);
            return;
        }

        _mediaPlayer.SetSpu(-1);
        _activeCustomSubtitleUrl = track.ExternalUrl;
        _customSubtitleCues = [];
        _activeSubtitleCueIndex = -1;
        CustomSubtitleText.Text = "";
        CustomSubtitleContainer.Visibility = Visibility.Collapsed;
        var cancellation = new CancellationTokenSource();
        _subtitleLoadCancellation = cancellation;

        try
        {
            var source = await _httpClient.GetStringAsync(track.ExternalUrl, cancellation.Token);
            if (cancellation.IsCancellationRequested || !string.Equals(_activeCustomSubtitleUrl, track.ExternalUrl, StringComparison.Ordinal))
            {
                return;
            }
            _customSubtitleCues = ParseSubtitleCues(source);
            ApplyCustomSubtitleAppearance();
            UpdateCustomSubtitle(_mediaPlayer.Time);
            PlaybackStatus.Text = _customSubtitleCues.Count > 0 ? "Altyazı hazır" : "Altyazı dosyası okunamadı";
        }
        catch (OperationCanceledException)
        {
        }
        catch
        {
            if (string.Equals(_activeCustomSubtitleUrl, track.ExternalUrl, StringComparison.Ordinal))
            {
                PlaybackStatus.Text = "Altyazı yüklenemedi";
            }
        }
        finally
        {
            if (ReferenceEquals(_subtitleLoadCancellation, cancellation))
            {
                _subtitleLoadCancellation.Dispose();
                _subtitleLoadCancellation = null;
            }
        }
    }

    private static List<SubtitleCue> ParseSubtitleCues(string source)
    {
        var normalized = source.Replace("\r\n", "\n").Replace('\r', '\n').TrimStart('\uFEFF');
        if (normalized.Contains("[Events]", StringComparison.OrdinalIgnoreCase) &&
            normalized.Contains("Dialogue:", StringComparison.OrdinalIgnoreCase))
        {
            return normalized.Split('\n')
                .Where(line => line.StartsWith("Dialogue:", StringComparison.OrdinalIgnoreCase))
                .Select(line => line["Dialogue:".Length..].Split(',', 10))
                .Where(parts => parts.Length == 10 && TryParseSubtitleTime(parts[1], out _) && TryParseSubtitleTime(parts[2], out _))
                .Select(parts =>
                {
                    TryParseSubtitleTime(parts[1], out var start);
                    TryParseSubtitleTime(parts[2], out var end);
                    return new SubtitleCue(start, end, CleanSubtitleText(parts[9]));
                })
                .Where(cue => cue.End > cue.Start && !string.IsNullOrWhiteSpace(cue.Text))
                .OrderBy(cue => cue.Start)
                .ToList();
        }

        var result = new List<SubtitleCue>();
        var lines = normalized.Split('\n');
        for (var index = 0; index < lines.Length; index++)
        {
            var arrow = lines[index].IndexOf("-->", StringComparison.Ordinal);
            if (arrow < 0)
            {
                continue;
            }
            var startText = lines[index][..arrow].Trim();
            var endText = lines[index][(arrow + 3)..].Trim().Split(' ', StringSplitOptions.RemoveEmptyEntries)[0];
            if (!TryParseSubtitleTime(startText, out var start) || !TryParseSubtitleTime(endText, out var end))
            {
                continue;
            }
            var textLines = new List<string>();
            while (++index < lines.Length && !string.IsNullOrWhiteSpace(lines[index]))
            {
                textLines.Add(lines[index].Trim());
            }
            var text = CleanSubtitleText(string.Join("\n", textLines));
            if (end > start && !string.IsNullOrWhiteSpace(text))
            {
                result.Add(new SubtitleCue(start, end, text));
            }
        }
        return result.OrderBy(cue => cue.Start).ToList();
    }

    private static bool TryParseSubtitleTime(string value, out long milliseconds)
    {
        var clean = value.Trim().Replace(',', '.');
        if (TimeSpan.TryParse(clean, System.Globalization.CultureInfo.InvariantCulture, out var time))
        {
            milliseconds = (long)time.TotalMilliseconds;
            return true;
        }
        milliseconds = 0;
        return false;
    }

    private static string CleanSubtitleText(string value)
    {
        var clean = value.Replace("\\N", "\n", StringComparison.OrdinalIgnoreCase)
            .Replace("\\n", "\n", StringComparison.OrdinalIgnoreCase);
        clean = Regex.Replace(clean, @"\{\\[^}]*\}", "");
        clean = Regex.Replace(clean, @"<[^>]+>", "");
        return WebUtility.HtmlDecode(clean).Trim();
    }

    private void ApplyCustomSubtitleAppearance()
    {
        var preferences = _activeRequest?.Preferences;
        var scale = Math.Clamp(preferences?.SubtitleSize ?? 100, 70, 180) / 100d;
        CustomSubtitleText.FontSize = Math.Clamp(32d * scale, 20d, 58d);
        CustomSubtitleText.LineHeight = CustomSubtitleText.FontSize * 1.18d;
        try
        {
            CustomSubtitleText.Foreground = (Brush)new BrushConverter().ConvertFromString(NormalizeSubtitleColor(preferences?.SubtitleColor))!;
        }
        catch
        {
            CustomSubtitleText.Foreground = Brushes.White;
        }
        var background = preferences?.SubtitleBackground ?? "shadow";
        CustomSubtitleContainer.Background = string.Equals(background, "box", StringComparison.OrdinalIgnoreCase)
            ? new SolidColorBrush(Color.FromArgb(210, 0, 0, 0))
            : Brushes.Transparent;
        CustomSubtitleText.Effect = string.Equals(background, "shadow", StringComparison.OrdinalIgnoreCase)
            ? new DropShadowEffect { Color = Colors.Black, BlurRadius = 7, ShadowDepth = 2, Opacity = 1 }
            : null;
    }

    private void UpdateCustomSubtitle(long currentTime)
    {
        if (_customSubtitleCues.Count == 0)
        {
            CustomSubtitleContainer.Visibility = Visibility.Collapsed;
            return;
        }
        var index = _activeSubtitleCueIndex;
        if (index < 0 || index >= _customSubtitleCues.Count ||
            currentTime < _customSubtitleCues[index].Start || currentTime > _customSubtitleCues[index].End)
        {
            index = _customSubtitleCues.FindIndex(cue => currentTime >= cue.Start && currentTime <= cue.End);
        }
        if (index == _activeSubtitleCueIndex)
        {
            return;
        }
        _activeSubtitleCueIndex = index;
        if (index < 0)
        {
            CustomSubtitleText.Text = "";
            CustomSubtitleContainer.Visibility = Visibility.Collapsed;
            return;
        }
        CustomSubtitleText.Text = _customSubtitleCues[index].Text;
        CustomSubtitleContainer.Visibility = Visibility.Visible;
    }

    private void ClearCustomSubtitles()
    {
        _subtitleLoadCancellation?.Cancel();
        _subtitleLoadCancellation?.Dispose();
        _subtitleLoadCancellation = null;
        _activeCustomSubtitleUrl = null;
        _customSubtitleCues = [];
        _activeSubtitleCueIndex = -1;
        CustomSubtitleText.Text = "";
        CustomSubtitleContainer.Visibility = Visibility.Collapsed;
    }

    private static string InferLanguagePreference(string trackName, string? fallback)
    {
        foreach (var language in new[] { "tr", "en", "de", "fr", "es", "ar" })
        {
            if (TrackMatchesLanguage(trackName, language))
            {
                return language;
            }
        }
        return string.IsNullOrWhiteSpace(fallback) ? "auto" : fallback;
    }

    private void PlaybackRates_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_updatingPlayerOptions || PlaybackRates.SelectedItem is not RateOption option || _activeRequest is null)
        {
            return;
        }

        var preferences = _activeRequest.Preferences ??= new NativePlayerPreferences();
        preferences.PlaybackRate = option.Value;
        _mediaPlayer.SetRate(option.Value);
        SendPlaybackPreferences();
    }

    private void SubtitleSizes_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_updatingPlayerOptions || SubtitleSizes.SelectedItem is not SubtitleSizeOption option || _activeRequest is null)
        {
            return;
        }

        var preferences = _activeRequest.Preferences ??= new NativePlayerPreferences();
        preferences.SubtitleSize = option.Value;
        SendPlaybackPreferences();
        ApplyCustomSubtitleAppearance();
        UpdateCustomSubtitle(_mediaPlayer.Time);
    }

    private void SubtitleColors_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_updatingPlayerOptions || SubtitleColors.SelectedItem is not SubtitleColorOption option || _activeRequest is null)
        {
            return;
        }

        var preferences = _activeRequest.Preferences ??= new NativePlayerPreferences();
        preferences.SubtitleColor = option.Value;
        SendPlaybackPreferences();
        ApplyCustomSubtitleAppearance();
        UpdateCustomSubtitle(_mediaPlayer.Time);
    }

    private void SendPlaybackPreferences()
    {
        var preferences = _activeRequest?.Preferences;
        if (preferences is null)
        {
            return;
        }

        SendWebEvent("streamlivex:native-settings", new
        {
            audioLanguage = preferences.AudioLanguage ?? "auto",
            subtitleMode = preferences.SubtitleMode ?? "auto",
            subtitleLanguage = preferences.SubtitleLanguage ?? "auto",
            playbackRate = preferences.PlaybackRate ?? 1f,
            subtitleSize = preferences.SubtitleSize ?? 100,
            subtitleColor = preferences.SubtitleColor ?? "#ffffff",
            subtitleBackground = preferences.SubtitleBackground ?? "shadow"
        });
    }

    private async void DownloadButton_Click(object sender, RoutedEventArgs e)
    {
        if (_downloadCancellation is not null)
        {
            _downloadCancellation.Cancel();
            DownloadButton.Content = "⌛";
            return;
        }

        var item = _activeRequest?.Item;
        if (item?.Url is null || string.Equals(item.Kind, "live", StringComparison.OrdinalIgnoreCase) ||
            !Uri.TryCreate(item.Url, UriKind.Absolute, out var source))
        {
            PlaybackStatus.Text = "Yalnızca film ve dizi bölümleri indirilebilir";
            return;
        }

        var extension = Path.GetExtension(source.AbsolutePath).ToLowerInvariant();
        if (extension == ".m3u8")
        {
            PlaybackStatus.Text = "Bu HLS kaynağı tek dosya olarak indirilemiyor";
            return;
        }
        if (extension is not ".mp4" and not ".mkv" and not ".ts" and not ".avi" and not ".mov")
        {
            extension = ".mp4";
        }

        var dialog = new SaveFileDialog
        {
            Title = "Videoyu kaydet",
            FileName = $"{SafeFileName(item.Name ?? "StreamLiveX")}{extension}",
            DefaultExt = extension,
            Filter = "Video dosyaları|*.mp4;*.mkv;*.ts;*.avi;*.mov|Tüm dosyalar|*.*",
            AddExtension = true,
            OverwritePrompt = true
        };
        if (dialog.ShowDialog(this) != true)
        {
            return;
        }

        var destination = dialog.FileName;
        _downloadCancellation = new CancellationTokenSource();
        DownloadButton.IsEnabled = true;
        DownloadButton.Content = "✕";
        PlaybackStatus.Text = "İndirme hazırlanıyor";

        try
        {
            using var response = await _httpClient.GetAsync(source, HttpCompletionOption.ResponseHeadersRead, _downloadCancellation.Token);
            response.EnsureSuccessStatusCode();
            var mediaType = response.Content.Headers.ContentType?.MediaType ?? "";
            if (mediaType.Contains("mpegurl", StringComparison.OrdinalIgnoreCase))
            {
                throw new InvalidOperationException("Bu HLS kaynağı tek dosya olarak indirilemiyor.");
            }

            var total = response.Content.Headers.ContentLength;
            await using var input = await response.Content.ReadAsStreamAsync(_downloadCancellation.Token);
            await using var output = new FileStream(destination, FileMode.Create, FileAccess.Write, FileShare.Read, 131072, true);
            var buffer = new byte[131072];
            long downloaded = 0;
            var lastProgress = -1;
            while (true)
            {
                var read = await input.ReadAsync(buffer, _downloadCancellation.Token);
                if (read == 0)
                {
                    break;
                }

                await output.WriteAsync(buffer.AsMemory(0, read), _downloadCancellation.Token);
                downloaded += read;
                if (total is > 0)
                {
                    var progress = (int)Math.Clamp(downloaded * 100 / total.Value, 0, 100);
                    if (progress != lastProgress)
                    {
                        lastProgress = progress;
                        PlaybackStatus.Text = $"İndiriliyor · %{progress}";
                    }
                }
                else
                {
                    PlaybackStatus.Text = $"İndiriliyor · {downloaded / 1048576d:0.0} MB";
                }
            }

            PlaybackStatus.Text = "İndirme tamamlandı";
        }
        catch (OperationCanceledException)
        {
            TryDeletePartialDownload(destination);
            PlaybackStatus.Text = "İndirme iptal edildi";
        }
        catch (Exception error)
        {
            TryDeletePartialDownload(destination);
            PlaybackStatus.Text = error is InvalidOperationException ? error.Message : "İndirme tamamlanamadı";
        }
        finally
        {
            _downloadCancellation.Dispose();
            _downloadCancellation = null;
            DownloadButton.Content = "⇩";
            DownloadButton.IsEnabled = !string.Equals(_activeRequest?.Item?.Kind, "live", StringComparison.OrdinalIgnoreCase);
        }
    }

    private static string SafeFileName(string value)
    {
        var invalid = Path.GetInvalidFileNameChars();
        var clean = new string(value.Select(character => invalid.Contains(character) ? '_' : character).ToArray()).Trim();
        return string.IsNullOrWhiteSpace(clean) ? "StreamLiveX" : clean;
    }

    private static void TryDeletePartialDownload(string path)
    {
        try
        {
            if (File.Exists(path)) File.Delete(path);
        }
        catch
        {
            // Dosya başka bir işlem tarafından açıldıysa kullanıcı verisini zorla silme.
        }
    }

    private void Fullscreen_Click(object sender, RoutedEventArgs e) => ToggleFullscreen();

    private void NextEpisode_Click(object sender, RoutedEventArgs e)
    {
        // Sonraki bölüme geçişi web tarafı yönetir (sezon bölüm listesi/indeksi orada). Web olayını
        // gönderiyoruz; web yeni bir "play" isteği postaladığında bu oynatıcı sonraki bölümü açar.
        NextEpisodeButton.Visibility = Visibility.Collapsed;
        SendWebEvent("streamlivex:native-player-next");
    }

    private void ToggleFullscreen()
    {
        if (!_isFullscreen)
        {
            // Mevcut durumu sakla ki cikista aynen geri yukleyebilelim.
            _preFullscreenState = WindowState;
            _preFullscreenStyle = WindowStyle;
            _preFullscreenResize = ResizeMode;
            _isFullscreen = true;
            WindowStyle = WindowStyle.None;
            ResizeMode = ResizeMode.NoResize;
            // Maximized'ken WindowStyle degistirmek WPF'te restore-bounds'u bozabiliyor;
            // once Normal'a alip sonra Maximized yaparak tam ekranin dogru ekrana yayilmasini
            // garanti ediyoruz.
            WindowState = WindowState.Normal;
            WindowState = WindowState.Maximized;
        }
        else
        {
            _isFullscreen = false;
            WindowStyle = _preFullscreenStyle;
            ResizeMode = _preFullscreenResize;
            WindowState = _preFullscreenState;
        }
    }

    private void Window_KeyDown(object sender, KeyEventArgs e)
    {
        if (NativePlayer.Visibility != Visibility.Visible)
        {
            return;
        }

        ShowPlayerChrome();

        switch (e.Key)
        {
            case Key.Escape when _isFullscreen:
                ToggleFullscreen();
                break;
            case Key.Escape:
                CloseNativePlayer();
                break;
            case Key.Space:
                PlayPause_Click(sender, e);
                break;
            case Key.Left when PositionSlider.IsEnabled:
                _mediaPlayer.Time = Math.Max(0, _mediaPlayer.Time - 10_000);
                break;
            case Key.Right when PositionSlider.IsEnabled:
                _mediaPlayer.Time = Math.Min(_mediaPlayer.Length, _mediaPlayer.Time + 10_000);
                break;
        }
    }

    private async void SendWebEvent(string eventName, object? detail = null)
    {
        if (Browser.CoreWebView2 is null)
        {
            return;
        }

        var eventJson = JsonSerializer.Serialize(eventName);
        var detailJson = JsonSerializer.Serialize(detail);
        await Browser.CoreWebView2.ExecuteScriptAsync($"window.dispatchEvent(new CustomEvent({eventJson}, {{ detail: {detailJson} }}));");
    }

    private async void RetryBrowser_Click(object sender, RoutedEventArgs e) => await InitializeBrowserAsync();

    private void DisposePlayer()
    {
        _mediaPlayer.Stop();
        _downloadCancellation?.Cancel();
        ClearCustomSubtitles();
        _activeMedia?.Dispose();
        VideoSurface.MediaPlayer = null;
        VideoSurface.Dispose();
        _mediaPlayer.Dispose();
        _libVlc.Dispose();
        _httpClient.Dispose();
    }
}

internal sealed record TrackOption(int Id, string Name, string? ExternalUrl = null, string? Language = null)
{
    public override string ToString() => Name;
}

internal sealed record SubtitleCue(long Start, long End, string Text);

internal sealed record RateOption(float Value, string Name)
{
    public override string ToString() => Name;
}

internal sealed record SubtitleSizeOption(int Value, string Name)
{
    public override string ToString() => Name;
}

internal sealed record SubtitleColorOption(string Value, string Name)
{
    public override string ToString() => Name;
}

internal sealed class NativePlayMessage
{
    public string? Type { get; set; }
    public string? SessionId { get; set; }
    public long? ResumeTime { get; set; }
    public NativeMediaItem? Item { get; set; }
    public NativePlayerPreferences? Preferences { get; set; }
}

internal sealed class NativeMediaItem
{
    public string? Name { get; init; }
    public string? Url { get; init; }
    public string? Kind { get; init; }
    public bool HasNext { get; init; }
    public List<NativeSubtitle> Subtitles { get; init; } = [];
}

internal sealed class NativeSubtitle
{
    public string? Src { get; init; }
    public string? Label { get; init; }
    public string? Language { get; init; }
}

internal sealed class NativePlayerPreferences
{
    public string? AudioLanguage { get; set; }
    public string? SubtitleMode { get; set; }
    public string? SubtitleLanguage { get; set; }
    public int? SubtitleSize { get; set; }
    public string? SubtitleColor { get; set; }
    public string? SubtitleBackground { get; set; }
    public float? PlaybackRate { get; set; }
}
