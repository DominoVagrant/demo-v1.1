package net.prominic.domino.vagrant;

import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import lotus.domino.ACL;
import lotus.domino.Database;
import lotus.domino.DbDirectory;
import lotus.domino.DocumentCollection;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.NotesThread;
import lotus.domino.Session;
import lotus.domino.View;

/**
 * Check access to the indicated servers, and get the full server name, including the organization,
 * This was originally created to test different methods of retrieving the server name.
 *
 */
public class CheckServer {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Insufficient Arguments.  Usage:  ");
            System.out.println("java -jar CheckServer.jar <server>");
            System.exit(1);
        }
        String serverName = args[0];


        try {

            NotesThread.sinitThread();
            
            Session session = NotesFactory.createSession();
            System.out.println("Running on Notes Version:  '" + session.getNotesVersion() + "'.");

            
            checkServer(session, serverName);

        }
        catch (Throwable throwable) {
            System.out.println("FAILED!");
            throwable.printStackTrace();
        }
        finally {
            NotesThread.stermThread();
        }
    }
    
    
    public static void checkServer(Session session, String serverName) throws NotesException, Exception {
        DbDirectory directory = null;
        Database database = null;
        try {

            directory = session.getDbDirectory(serverName);
            if (null == directory) {
                System.out.println("Unable to open directory for server '" + serverName + "'.");
            }
            else {
//                System.out.println("Successfully opened directory for server '" + serverName + "'.");
                // NOTE:  This doesn't work because it only returns the common name
                System.out.println("DbDirectory.getName():  '" + directory.getName() + "'");
                String commonName = directory.getName();
                database = directory.getFirstDatabase(DbDirectory.DATABASE);
                if (null == database) {
                    System.out.println("Unable to open database for server '" + serverName + "'.");
                }
                else {
                    System.out.println("The first database on server '" + serverName + "' was '" + database.getTitle() + "'.");
                    // NOTE:  This doesn't work because it only returns the common name
                    System.out.println("Database.getServer():  '" + database.getServer() + "'");
                }
                
                
                // lookup the server document using the common name
                String lookupServerName = getFullServerName(session, serverName, commonName);
                System.out.println("Lookup from names.nsf:  '" + lookupServerName + "'.");

            }
            
            // try the console instead:  requires console access
            String output = session.sendConsoleCommand(serverName, "!show server");
            //System.out.println("session.sendConsoleCommand(server, !show server):  '" + output + "'.");
/*
Server name:            domino-49.prominic.net/PNI - 00BD - Development
Domain name:            PNI
*/

            // NOTE:  Extract the abbreviated server name, i.e. `demo.startcloud.com/STARTcloud`.  The organization could be extracted using the Name class
            // Supports server names with an organization unit (OU=)
            // The server title needs to be filtered from the end of the line
            Pattern serverPattern = Pattern.compile("Server name:\\s+(\\S.*?\\S)\\s+-.*$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
            Matcher serverMatcher = serverPattern.matcher(output);
            if (serverMatcher.find()) {
                System.out.println("Console 'Server name':  '" + serverMatcher.group(1) + "'");
            }
            
            // NOTE:  This extracts the domain/organization only.  This
            Pattern domainPattern = Pattern.compile("Domain name:\\s+(\\S.*)\\s*$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
            Matcher domainMatcher = domainPattern.matcher(output);
            if (domainMatcher.find()) {
                System.out.println("Console 'Directory':  '" + domainMatcher.group(1) + "'");
            }
           

        }
        catch (NotesException ex) {
            throw new Exception("Unable to open server '" + serverName + "':  '" + ex.text + "'.");
        }
        finally {
            if (null != database) { database.recycle(); }
            if (null != directory) { directory.recycle(); }
        }
    }

    /**
     * Given a common name retrieved from DbDirectory.getName or Database.getServerName, lookup the full, canonical name from names.nsf
     * Requires at least reader access to names.nsf.
     * 
     * @param originalServerName  the name or address used to reach the server
     * @param commonName  the common name
     * @return  the canonical server name
     * @throws NotesException  if an error occurred in the Notes API
     * @throws Exception  if the database, view, or server document could not be opened
     */
    protected static String getFullServerName(Session session, String originalServerName, String commonName) throws NotesException, Exception {
        Database namesDB = null;
        View lookupView = null;
        DocumentCollection matches = null;
        Document serverDoc = null;
       
        try {
        
            namesDB = session.getDatabase(originalServerName, "names.nsf", false);  
            
            if (null == namesDB || !namesDB.isOpen()) {
                // TODO:  support names.nsf at other locations?
                throw new Exception("Could not open names.nsf.");
            }
            
            lookupView = namesDB.getView("($Servers)");
            if (null == lookupView) {
                throw new Exception("Could not open server lookup view.");
            }
            
            // The names in this view are cannonical, so I need to do a fuzzy search
            // format  the lookup string to match the start of the name
            matches = lookupView.getAllDocumentsByKey("CN=" + commonName + "/", false);
            if (matches.getCount() < 1) {
                throw new Exception("No matches for server name '" + commonName + "'.");
            }
            else if (matches.getCount() > 1) {
                throw new Exception("Multiple mathches for server name '" + commonName + "'.");
            }
            else {
                serverDoc = matches.getFirstDocument();
                String serverName = serverDoc.getItemValueString("ServerName");
                return serverName;
            }
        }
        finally {
            if (null != serverDoc) { serverDoc.recycle(); }
            if (null != lookupView) { lookupView.recycle(); }
            if (null != matches) { matches.recycle(); }
            if (null != namesDB) { namesDB.recycle(); }
        }
    }
}
