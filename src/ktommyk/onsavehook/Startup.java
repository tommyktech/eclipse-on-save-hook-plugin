package ktommyk.onsavehook;

import org.eclipse.ui.IStartup;
public class Startup implements IStartup {
	@Override
	public void earlyStartup() {
		System.out.println("starting OnSaveHookPlugin...;");
	}
}
