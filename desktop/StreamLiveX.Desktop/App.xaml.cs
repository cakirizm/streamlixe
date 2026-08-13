using System.Windows;
using LibVLCSharp.Shared;

namespace StreamLiveX.Desktop;

public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        Core.Initialize();
        base.OnStartup(e);

        var window = new MainWindow();
        MainWindow = window;
        window.Show();
        if (e.Args.Length > 0)
        {
            window.ActivateFromProtocol(e.Args[0]);
        }
    }
}
