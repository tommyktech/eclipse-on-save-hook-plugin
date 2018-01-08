package ktommyk.onsavehook;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Color;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "OnSaveHookPlugin"; //$NON-NLS-1$

	// The shared instance
	private static Activator plugin;
	
	
	private HashMap<String, String> projExecMap = new HashMap<String, String>();
	private HashMap<String, List<String>> projIncludeMap = new HashMap<String, List<String>>();
	private HashMap<String, List<String>> projExcludeMap = new HashMap<String, List<String>>();
	// configuration file paths and their timestamps, used to check whether the configuration has changed or not
	private HashMap<String, Long> confFiles = new HashMap<String, Long>();
	
	// configuration file name of each project
	private String confFileName = "on_save_hook.conf";
	private int hookInterval = 1000;
	
    final static Color WARN_COLOR  = new Color(null, 255, 120, 0); 
    final static Color ERROR_COLOR = new Color(null, 255, 0, 0); 
    
	private static ExecutorService executor;
	private static long lastChanged = 0;
	static {
		executor = Executors.newSingleThreadExecutor();
	}
	
	/**
	 * The constructor
	 */
	public Activator() {
		loadGlobalConfig();
		loadConfigs();
		try {
			IResourceChangeListener rcl = new IResourceChangeListener() {
				@Override
				public void resourceChanged(IResourceChangeEvent event) {
					if (System.currentTimeMillis() - lastChanged < hookInterval) {
						lastChanged = System.currentTimeMillis();
						return ;
					}
					lastChanged = System.currentTimeMillis();
					
					Runnable runnable = new Runnable() {
					    public void run() {
							// reload config
							loadConfigs();
					        IResourceDelta rootDelta = event.getDelta();
		
					        HashMap<String, ArrayList<String>> changed = new HashMap<String, ArrayList<String>>();
					        IResourceDeltaVisitor visitor = new IResourceDeltaVisitor() {
					            public boolean visit(IResourceDelta delta) {
					            	if (delta.getResource().getLocation().toFile().isDirectory()) {
					            		return true;
					            	}
					               if (delta.getKind() != IResourceDelta.CHANGED
					            		   && delta.getKind() != IResourceDelta.ADDED
					            		   && delta.getKind() != IResourceDelta.REMOVED) {
					            	   System.out.println("the kind is neither CHANGED nor ADDED nor REMOVED: ".concat(Integer.toString(delta.getKind())));
					            	   System.out.println(delta.getResource().getFullPath());
					                  return true;
					               }
					               
					               IResource resource = delta.getResource();
					               if (resource.getType() == IResource.FILE) {
		//			            		   && "java".equalsIgnoreCase(resource.getFileExtension())) {
					            	   String fileName = resource.getName();
		
					            	   String kind = "";
					            	   if (delta.getKind() == IResourceDelta.CHANGED) {
					            		   kind = "change";
					            	   } else if (delta.getKind() == IResourceDelta.ADDED) {
					            		   kind = "create";
					            	   } else if (delta.getKind() == IResourceDelta.REMOVED) {
					            		   kind = "remove";
					            	   }
					            	   
					            	   // search for target paths
					            	   for (String projPath : projExecMap.keySet()) {
					            		   if (resource.getLocation().toString().indexOf(projPath) != 0) {
					            			   continue;
					            		   }
					            		   if (projIncludeMap.containsKey(projPath)) {
					            			   List<String> includeRegexList = projIncludeMap.get(projPath);
					            			   boolean matched = false;
					            			   for (String indcludeRegex : includeRegexList) {
					            				   try {
					            					   boolean res = Pattern.matches(indcludeRegex, fileName);
						            				   if (res) {
						            					   matched = true;
						            					   break;
						            				   }
					            				   } catch (Exception e) {
					            					   printError(getStackTraceString(e));
					            				   }
					            			   }
					            			   if (matched == false) {
					            				   continue;
					            			   }
					            		   }
					            		   if (projExcludeMap.containsKey(projPath)) {
					            			   List<String> excludeRegexList = projExcludeMap.get(projPath);
					            			   boolean matched = false;
					            			   for (String excludeRegex : excludeRegexList) {
					            				   try {
							            			   boolean res = Pattern.matches(excludeRegex, fileName);
						            				   if (res) {
						            					   matched = true;
						            					   break;
						            				   }
					            				   } catch (Exception e) {
					            					   printError(getStackTraceString(e));
					            				   }
					            			   }
					            			   if (matched) {
					            				   continue;
					            			   }
					            		   }
					            		   
					            		   // add this file to the changed list
					            		   if (!changed.containsKey(projPath)) {
					            			   changed.put(projPath, new ArrayList<String>());
					            		   }

		            					   printInfo(kind.concat(": ").concat(resource.getFullPath().toOSString()));
					            		   changed.get(projPath).add(kind.concat("=").concat(resource.getLocation().toOSString()));
					            	   }
					               }
					               return true;
					            }
					         };
					         try {
					        	 rootDelta.accept(visitor);
					         } catch (CoreException e) {
					        	 printError(getStackTraceString(e));
					         }
					         
			        		 for (String projPath : changed.keySet()) {
			        			 String execCommand = projExecMap.get(projPath);
			        			 List<String> args = changed.get(projPath);
			        			 args.add(0, projPath);
			        			 args.add(0, execCommand);
					        	 printInfo("executing command: ");
			        			 for (String arg : args) {
			        				 printInfo(arg.concat(" "));
			        			 }
			        			 ProcessBuilder pb = new ProcessBuilder(args);
		        				 try {
									Process process = pb.start();
									pb.redirectErrorStream(true);
		
					    	        String str;
					    	        BufferedReader brerr = new BufferedReader(new InputStreamReader(process.getInputStream()));
					    	        try {
					    	            while((str = brerr.readLine()) != null) {
					    	            	printInfo(str);
					    	            }
					    	        } catch (IOException e) {
							        	 printError(getStackTraceString(e));
					    	        } finally {
					    	            try {
					    	                brerr.close();
					    	            } catch (IOException e) {
								        	 printError(getStackTraceString(e));
					    	            }
					    	        }
								} catch (IOException e) {
						        	 printError(getStackTraceString(e));
								}
			        		 }
						}
			        };

//			        job.setUser(true);
//			        job.schedule();
					executor.submit(runnable);
				}
			};
			ResourcesPlugin.getWorkspace().addResourceChangeListener(rcl);
		} catch (Exception ex) {
       	 	printError(getStackTraceString(ex));
		}

	}

	private void printInfo(String str) {
	    MessageConsole console = findConsole("");
	    MessageConsoleStream stream = console.newMessageStream();
	    stream.println(str);

		try {
			stream.close();
		} catch (IOException e) {
			System.out.println("failed to close stream");
		}
	}
	
	private void printError(String str) {
	    MessageConsole console = findConsole("");
	    MessageConsoleStream stream = console.newMessageStream();
	    stream.setColor(ERROR_COLOR);
	    stream.println(str);
		try {
			stream.close();
		} catch (IOException e) {
			System.out.println("failed to close stream");
		}
	}
	
	@SuppressWarnings("unused")
	private void printInputStream(InputStream is, MessageConsoleStream mcs) throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		try {
			for (;;) {
				String line = br.readLine();
				if (line == null) break;
				mcs.println(line);
			}
		} finally {
			br.close();
		}
	}
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;

	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}

	/**
	 * Returns an image descriptor for the image file at the given
	 * plug-in relative path
	 *
	 * @param path the path
	 * @return the image descriptor
	 */
	public static ImageDescriptor getImageDescriptor(String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}

	/**
	 * Load plugin global configuration from [workspace path]/.eclipse/on_save_hook_global.conf
	 * @return
	 */
	private boolean loadGlobalConfig() {
		String workspaceDir = ResourcesPlugin.getWorkspace().getRoot().getLocation().toString().concat("/");
		String packageName = this.getClass().getPackage().getName();
		String confDirStr = workspaceDir.concat(".eclipse/").concat(packageName);
		
		// create global config directory if not exists 
		File confDir = new File(confDirStr);
		if (confDir.exists() == false) {
			boolean res = confDir.mkdirs();
			if (res) {
				System.out.println("created directory: ".concat(confDirStr));
			} else {
				System.out.println("failed to create directory: ".concat(confDirStr));
				return false;
			}
		}
		
		// read global config file 
		String confFilePath = confDirStr.concat("/on_save_hook_global.conf");
		File   confFile     = new File(confDirStr.concat("/on_save_hook_global.conf"));
		if (confFile.exists() == false) {
		    try {
		    	confFile.createNewFile();
		    	Properties prop = new Properties();
			    try {
			    	prop.put("conf_file_name", "on_save_hook.conf");
			    	prop.put("hook_interval",  "1000");
			        FileOutputStream stream = new FileOutputStream(confFile);
			        prop.store(stream, "");
			    } catch (IOException e) {
			    	printError(getStackTraceString(e));
			       	return false;
			    }
		    } catch (IOException e) {
		    	printError(getStackTraceString(e));
	        	return false;
		    }
		}
    	Properties prop = new Properties();
	    try {
	        prop.load(new FileInputStream(confFilePath));
	    } catch (IOException e) {
	    	printError(getStackTraceString(e));
	       	return false;
	    }
	    
	    if (prop.containsKey(("conf_file_name"))) {
	    	this.confFileName = prop.getProperty("conf_file_name");
	    }
	    
	    if (prop.containsKey(("hook_interval"))) {
	    	this.hookInterval = Integer.parseInt(prop.getProperty("hook_interval"));
	    }
	    
		return true;
	}
	
	/**
	 * Load configurations of each project
	 */
	private void loadConfigs() {
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (IProject proj : projects) {
			String projectPathStr = proj.getLocation().toString().concat("/");
			String confFileStr = projectPathStr.concat(confFileName);
			File confFile = new File(confFileStr);
			if (confFile.exists()) {
				Long lastModified = confFile.lastModified();
				if (confFiles.containsKey(confFileStr)) {
					if (!confFiles.get(confFileStr).equals(lastModified)) {
						boolean res = loadConfig(confFile);
						if (res) {
							confFiles.put(confFileStr, lastModified);
						}
					}
				} else {
					boolean res = loadConfig(confFile);
					if (res) {
						confFiles.put(confFileStr, lastModified);
					}
				}
			}
		}
	}
	
	
	private boolean loadConfig(File confFile) {
		Properties prop = new Properties();
	    try {
	    	System.out.println("confFile.getAbsolutePath(): ".concat(confFile.getAbsolutePath()));
	    	prop.load(new FileInputStream(confFile.getAbsolutePath()));
	    } catch (IOException e) {
//	    	errorConsole.println(getStackTraceString(e));
	    	return false;
	    }
	    
    	// Set project path as default 
	    String dir = confFile.getParent();
	    if (prop.containsKey("dir") && prop.getProperty("dir").equals("") == false) {
	    	dir = prop.getProperty("dir");
	    }
    	// conver this dir path from Windows style to Unix one. ( \ => / )
	    IPath path = new Path(dir);
	    String dirPathStr = path.toString();
	    
	    if (prop.containsKey("exec") && prop.getProperty("exec").equals("") == false) {
	    	projExecMap.put(dirPathStr, prop.getProperty("exec"));
	    } else {
	    	return false;
	    }
	    
	    if (prop.containsKey("include") && prop.getProperty("include").equals("") == false) {
	    	String includeStr = prop.getProperty("include");
	    	List<String> includeStrList = new ArrayList<String>();
	    	for (String include : includeStr.split(",")) {
	    		includeStrList.add(wildcardToRegex(include));	    		
	    	}
	    	projIncludeMap.put(dirPathStr, includeStrList);
	    }
	    if (prop.containsKey("exclude") && prop.getProperty("exclude").equals("") == false) {
	    	String excludeStr = prop.getProperty("exclude");
	    	List<String> excludeStrList = new ArrayList<String>();
	    	for (String exclude : excludeStr.split(",")) {
	    		excludeStrList.add(wildcardToRegex(exclude));	    		
	    	}
	    	projExcludeMap.put(dirPathStr, excludeStrList);
	    }
	    
    	return true;
	}

	/**
	 * Convert wildcard syntaxes to regex ones
	 * Added some improvement on http://www.rgagnon.com/javadetails/java-0515.html
	 * @param wildcard like "foo*bar?tar"
	 * @return
	 */
    private String wildcardToRegex(String wildcard){
        StringBuffer s = new StringBuffer(wildcard.length());
        s.append('^');
        for (int i = 0, is = wildcard.length(); i < is; i++) {
            char c = wildcard.charAt(i);
            switch(c) {
                case '*':
                    s.append(".*");
                    break;
                case '?':
                    s.append("\\w");
                    break;
                    // escape special regexp-characters
                case '(': case ')': case '[': case ']': case '$':
                case '^': case '.': case '{': case '}': case '|':
                case '\\':
                    s.append("\\");
                    s.append(c);
                    break;
                default:
                    s.append(c);
                    break;
            }
        }
        s.append('$');
        return(s.toString());
    }
    
    private MessageConsole findConsole(String name) {
        ConsolePlugin plugin = ConsolePlugin.getDefault();
        IConsoleManager conMan = plugin.getConsoleManager();
        IConsole[] existing = conMan.getConsoles();
        for (int i = 0; i < existing.length; i++)
           if (name.equals(existing[i].getName()))
              return (MessageConsole) existing[i];
        //no console found, so create a new one
        MessageConsole myConsole = new MessageConsole(name, null);
        conMan.addConsoles(new IConsole[]{myConsole});
        return myConsole;
     }
    
    private String getStackTraceString(Exception ex) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		ex.printStackTrace(pw);
		String sStackTrace = sw.toString();
		return sStackTrace;
    }
}
