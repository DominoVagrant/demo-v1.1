package net.prominic.domino.vagrant;

import lotus.domino.*;

import java.io.FileInputStream;
import java.util.Date;
import java.util.Vector;

import org.json.JSONObject;
import org.json.JSONTokener;

/*

Domino 12 Java classes
https://help.hcltechsw.com/dom_designer/12.0.0/basic/H_10_NOTES_CLASSES_ATOZ_JAVA.html

Registration.crossCertify
https://help.hcltechsw.com/dom_designer/12.0.0/basic/H_CROSSCERTIFY_METHOD_JAVA.html

*/


public class CrossCertifyNotesID
{
	public static final String AUTHORIZED_GROUP = "AutomaticallyCrossCertifiedUsers";
	
	public static void main(String args[])
	{
		System.out.println("Starting cross-certification tool.");
		
		FileInputStream fis = null;
		try {
				
			// retrieve or compute the parameters
			
			if (args.length < 1) {
				throw new Exception("No ID file specified.");
			}
			String targetID = args[0]; // TODO:  support more files
			
			// The JSON file used for Domino server setup can also be used for for this configuration
			// TODO:  allow overrides from system properties?
			String settingsFile = "/local/dominodata/setup.json";  // TODO:  make this more configurable?
			fis = new FileInputStream(settingsFile);
			JSONObject json = (JSONObject)new JSONTokener(fis).nextValue();
			
			// extract the values
			// TODO:  add more validation if it becomes a problem. This code could easily trigger NullPointerExceptions if the format is invalid
			JSONObject serverSetup = json.getJSONObject("serverSetup");
			JSONObject serverConfig = serverSetup.getJSONObject("server");
			String name = serverConfig.getString("name");
			String org = serverConfig.getString("domainName");
			String server = name + "/" + org;
			
			String certID = "/local/dominodata/cert.id";  // Not in setup.json, so use convention
			String certPassword = serverSetup.getJSONObject("org").getString("certifierPassword");
			
			// currently we are using the admin user for actions like this
			String userPassword = serverSetup.getJSONObject("admin").getString("password");
			
			String userName = crossCertify(targetID, userPassword, server, certID, certPassword);
				
			System.out.println( "crossCertifyNotesID() completed.");
			
			// add the user to an authorized group
			if (null != userName) {
				addUserToAuthorizedGroup(userName, server, userPassword);
			}
			else {
				System.out.println("Could not detect user from safe ID.");
			}
		}
		catch (Throwable throwable) {
			throwable.printStackTrace();
		}
		finally {
			try {
				if (null != fis) { fis.close(); }
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
	
	
	
	
	/**
	 * Cross-certify the given targetID for the given server.
	 * @param targetID  the ID to sign
	 * @param userPassword  The password for the ID file being used to make this request
	 * @param server  the server to sign against
	 * @param certID  the cert ID for server
	 * @param certPassword  the password for certID
	 * @return  the name of the user that was cross-certified, or <code>null</code> if the user could not be identified
	 * @throws NotesException if an error occurred in the Notes API
	 * @throws Exception if the cross-certification failed
	 */
	public static String crossCertify(String targetID, String userPassword, String server, String certID, String certPassword)  throws Exception {
		System.out.println("Signing ID:  '" + targetID + "'.");
		try {
			NotesThread.sinitThread();
				
			 // build the session arguments
			String[] args = null;
			System.out.println("Using default notesID path.");
			args = new String[0];
		
			 //Session session = NotesFactory.createSession("localhost", args, "", "");
			//Session session = NotesFactory.createSession(null, args, null, null);
			String sessionServer = null; // local server
			String sessionUser = null;  // default user
			Session session = NotesFactory.createSession(sessionServer, args, sessionUser, userPassword);
			System.out.println("Running on Notes Version:  '" + session.getNotesVersion() + "'.");
				
							 
			AgentContext agentContext = session.getAgentContext();
		
			 // (Your code goes here) 
			Registration reg = session.createRegistration();
			reg.setRegistrationServer( server);
			reg.setCertifierIDFile( certID);
						
			DateTime dt = session.createDateTime("Today");
			dt.setNow();
			dt.adjustYear(1);	 
			reg.setExpiration(dt);
			
			// NOTE:  crossCertify triggers a password check even with an authenticated session, if the ID file has a password
			// I see this behavior is specifically noted for the recertify method, but not crossCertify:  https://help.hcltechsw.com/dom_designer/12.0.0/basic/H_RECERTIFY_METHOD_JAVA.html
			// Enter the password from the command prompt, or automate it using the "yes" command
			if (reg.crossCertify(targetID, 
				certPassword, // certifier password
				"programmatically cross certified")) // comment field
			{ 
				System.out.println("Cross-certification succeeded"); 
				
				// Lookup the cross-certification document to check the user name
				// I haven't found a better way to do this.
				return getLastCrossCertifiedUser(session, server);
			}
			else { 
				throw new Exception("Registration.crossCertify reported failure");
			}
		} 
//		catch(NotesException e) {
//			System.out.println( e.id + " " + e.text);
//			e.printStackTrace();
//		}
		finally {
			NotesThread.stermThread();
		}	
	}
	
	/**
	 * Get the last cross-certified user in names.nsf on the given server.
	 * I don't see a better way to extract the username from a Notes ID for now.
	 * Note that this agent won't throw an Exception.
	 * 
	 * @param session  the existing session
	 * @param server  the target server
	 * @return the username, or <code>null</code> if no valid user was found.
	 */
	public static String getLastCrossCertifiedUser(Session session, String server) {
		Database namesDatabase = null;
		View certView = null;
		ViewEntryCollection entries = null;
		try {
			namesDatabase = session.getDatabase(server, "names.nsf", false);
			if (null == namesDatabase || !namesDatabase.isOpen()) {
				throw new Exception("Could not open names.nsf");
			}
			
			certView = namesDatabase.getView("($CrossCertByName)");
			if (null == certView) {
				throw new Exception("Could not open cross-certificate view.");
			}
			
			entries = certView.getAllEntries();
			ViewEntry curEntry = entries.getFirstEntry();
			String userName = null;
			Date latestDate = null;
			while (null != curEntry) {
				Document curDoc = null;
				DateTime dateTime = null;
				try {
					if (curEntry.isDocument()) {
						curDoc = curEntry.getDocument();
						String issuedTo = curDoc.getItemValueString("IssuedTo");
						dateTime = curDoc.getLastModified();
						
						if (null == issuedTo || issuedTo.trim().isEmpty()) {
							System.out.println("Found cross-certificate document " + curDoc.getUniversalID() + " with no value for IssuedTo.");
							// Skip
						}
						else if (null == latestDate || dateTime.toJavaDate().after(latestDate)) {
							// this is the new latest document
							userName = issuedTo;
							latestDate = dateTime.toJavaDate();
						}
						
						
						
					}
					// not a document
				}
				finally {
					ViewEntry prevEntry = curEntry;
					curEntry = entries.getNextEntry();
					
					// cleanup
					if (null != dateTime) { dateTime.recycle(); }
					if (null != curDoc) { curDoc.recycle(); }
					prevEntry.recycle();
				}
			}
			
			if (null == userName || userName.trim().isEmpty()) {
				return null;  // normallize the output
			}
			return userName;
		}
		catch (Exception ex) {
			System.out.println("Failed to read last cross-certified user:  ");
			ex.printStackTrace(System.out);
			return null;
		}
		finally {
			try {
				if (null != entries) {
					entries.recycle();
				}
				if (null != certView) {
					certView.recycle();
				}
				if (null != namesDatabase) {
					namesDatabase.recycle();
				}
			}
			catch (NotesException ex) {
				System.out.println("Failed to recycle objects:  ");
				ex.printStackTrace(System.out);
			}
		}
	}
	
	/**
	 * Add the given username to the {@link #AUTHORIZED_GROUP} group on the target server.
	 * @param username the username to add
	 * @param server  the target server
	 * @param userPassword  the password for the running user (not the above username).
	 */
	public static void addUserToAuthorizedGroup(String username, String server, String userPassword) throws NotesException, Exception {
		System.out.println ("Adding user '" + username + "' to authorized user group (" + AUTHORIZED_GROUP + ").");
		Session session = null;
		Database namesDatabase = null;
		View groupView = null;
		Document groupDoc = null;
		Vector members = null;
		
		try {
			NotesThread.sinitThread();
				
			 // build the session arguments
			String[] args = null;
			System.out.println("Using default notesID path.");
			args = new String[0];
			String sessionServer = null; // local server
			String sessionUser = null;  // default user
			session = NotesFactory.createSession(sessionServer, args, sessionUser, userPassword);
			
			
			namesDatabase = session.getDatabase(server, "names.nsf", false);
			if (null == namesDatabase || !namesDatabase.isOpen()) {
				throw new Exception("Could not open names.nsf");
			}
			
			groupView = namesDatabase.getView("Groups");
			if (null == groupView) {
				throw new Exception("Could not open group view.");
			}
			
			groupDoc = groupView.getDocumentByKey(AUTHORIZED_GROUP, true);
			if (null == groupDoc) {
				throw new Exception("Could not find expected group document:  '" + AUTHORIZED_GROUP + "'.");
			}
			
			members = groupDoc.getItemValue("Members");
			if (null == members || members.size() == 0 ||
			    (members.size() == 1 && members.get(0).toString().trim().isEmpty())) { // default blank entry
				members = new Vector();  // normalize 	
			}
			members.add(username);
			groupDoc.replaceItemValue("Members", members);
			
			// save
			if (!groupDoc.save(true)) { // force the save
				throw new Exception("Could not update group document.");
			}
			else {
				System.out.println("Authorized group has been updated.");
			}
			
			session.recycle(members);
		}
		finally {
			if (null != members) { session.recycle(members);}
			if (null != groupDoc) { groupDoc.recycle();}
			if (null != groupDoc) { groupView.recycle();}
			if (null != groupDoc) { namesDatabase.recycle();}
			if (null != session) { session.recycle(); }
			NotesThread.stermThread();
		}
		
	}

	 	
}
