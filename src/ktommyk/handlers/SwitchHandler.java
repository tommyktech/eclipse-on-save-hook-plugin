package ktommyk.handlers;

import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.menus.UIElement;

import ktommyk.onsavehook.Activator;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;

/**
 * Our sample handler extends AbstractHandler, an IHandler base class.
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class SwitchHandler extends AbstractHandler implements IElementUpdater {
    private static ImageDescriptor image_enable = 
        Activator.getImageDescriptor("icons/hook.png");
    private static ImageDescriptor image_disable = 
        Activator.getImageDescriptor("icons/hook_disabled.png");
    public static boolean STATUS_FLAG = true;
    /**
     * The constructor.
     */
    public SwitchHandler() {
    }

    /**
     * the command has been executed, so extract extract the needed information
     * from the application context.
     */
    public Object execute(ExecutionEvent event) throws ExecutionException {
        String message = "";
        if (STATUS_FLAG == true) {
            STATUS_FLAG = false;
            message = "Disabled On Save Hook Plugin";
        } else {
            STATUS_FLAG = true;
            message = "Enabled On Save Hook Plugin";
        }
        
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
        ICommandService commandService = (ICommandService) window.getService(ICommandService.class);
        if (commandService != null) {
            commandService.refreshElements("OnSaveHookPlugin.commands.switchCommand", null);
        }
        
        MessageDialog.openInformation(
                window.getShell(),
                "OnSaveHookPlugin",
                message);
        return null;
    }

    @Override
    public void updateElement(UIElement element, @SuppressWarnings("rawtypes") Map map) {
        if (STATUS_FLAG == true) {
            element.setIcon(image_enable);
        } else {
            element.setIcon(image_disable);
        }
    }
}
