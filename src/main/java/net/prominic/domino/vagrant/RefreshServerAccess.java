package net.prominic.domino.vagrant;

import lotus.domino.*;

/**
 * Refresh the ($ServerAccess) view in names.nsf to fix cross-certificate issue.
 * @author joelanderson
 *
 */
public class RefreshServerAccess {

    public static void main(String[] args) {
        try {
            refreshViews(args);
        }
        catch (Throwable throwable) {
            throwable.printStackTrace();
        }

    }

    
    public static void refreshViews(String[] args) throws Exception {
        try {
        		if (args.length < 1) {
        			throw new Exception("Missing parameter for abbreviated server name.");
        		}
        		String serverName = args[0];
        		
            NotesThread.sinitThread();

//            Session session = NotesFactory.createSession("localhost", args, "", "");
            Session session = NotesFactory.createSession(null, args, null, null);
            Database database = session.getDatabase(serverName, "names.nsf", false);
            if (null == database || !database.isOpen()) {
            		throw new Exception("Could not open names.nsf database.");
            }
            
            // don't do this automatically so that I can control the order.
            //refreshView("($ServerAccess)", database, session);
            
            for (int i = 1; i < args.length; i++) {  // skip first parameter, which is the server
            		String curView = args[i];
            		refreshView(curView, database, session);
            }
        }
        catch (Throwable ex) {
        		ex.printStackTrace();
        		throw ex;
        }        
        finally {
            NotesThread.stermThread();
        }
        
    }
    
    public static void refreshView(String viewName, Database database, Session session) throws NotesException, Exception {
    		System.out.println("Refreshing view:  '" + viewName + "'");
		View refreshView = null;
		try {
			System.out.println("namesDatabase.getView('" + viewName + "'");
			refreshView = database.getView(viewName);
			if (null != refreshView) {
				System.out.println("refreshView.refresh()");
				refreshView.refresh();
			}
			else {
				System.out.println("Could not open view '" + viewName + "'.");
			}
		}
		catch (Exception ex) {
			System.out.println("Could not refresh view '" + viewName + "'.");
			ex.printStackTrace(System.out);
		}
		finally {
			if (null != refreshView) { refreshView.recycle(); }
		}
    }

}
