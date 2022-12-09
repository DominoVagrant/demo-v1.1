package net.prominic.domino.vagrant;

import lotus.domino.*;

import java.io.FileInputStream;
import java.util.Date;
import java.util.Vector;

import org.json.JSONArray;
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
				// This is required to fix the "Error validating execution rights" error.  The above group does not work as expected
				addUserAsServerAdmin(userName, server,userPassword);
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
			certView.refresh();   // avoid race conditions on view population
			certView.setAutoUpdate(false);  // avoid updates in the middle of iteration
			
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
			
			// computeWithForm - is this required?
			groupDoc.computeWithForm(false, true);
			
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
			if (null != groupView) { groupView.recycle();}
			if (null != namesDatabase) { namesDatabase.recycle();}
			if (null != session) { session.recycle(); }
			NotesThread.stermThread();
		}
		
	}

	/**
	 * Add the given username to the server document, with enough access for DXL Importer and agents to work.
	 * TODO:  This is massive overkill - it should be controlled by the group instead.   
	 * However, the group was not working in our recent tests, so I'm using this as a workaround for now.'
	 * @param username the username to add
	 * @param server  the target server
	 * @param userPassword  the password for the running user (not the above username).
	 */
	public static void addUserAsServerAdmin(String username, String server, String userPassword) throws NotesException, Exception {
		System.out.println ("Adding user '" + username + "' to server document as authorized user.");
		Session session = null;
		Database namesDatabase = null;
		View serverView = null;
		Document serverDoc = null;
		Name nameObj = null;
		
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
			
			serverView = namesDatabase.getView("($Servers)");
			if (null == serverView) {
				throw new Exception("Could not open server view.");
			}
			
			nameObj = session.createName(server);
			String key = nameObj.getCanonical();
			
			serverDoc = serverView.getDocumentByKey(key, true);
			if (null == serverDoc) {
				throw new Exception("Could not find expected server document:  '" + server + "'.");
			}
			
			// Track if any of the fields are update.
			// This will support rerunning the agent for the same ID.
			boolean updated = false;
			
			// update the security fields
			updated = updateServerSecurityField(serverDoc, "FullAdmin", username) ? true : updated;
			updated = updateServerSecurityField(serverDoc, "CreateAccess", username) ? true : updated;
			updated = updateServerSecurityField(serverDoc, "ReplicaAccess", username) ? true : updated;
			updated = updateServerSecurityField(serverDoc, "UnrestrictedList", username) ? true : updated;
			updated = updateServerSecurityField(serverDoc, "OnBehalfOfInvokerLst", username) ? true : updated;
			updated = updateServerSecurityField(serverDoc, "LibsLst", username) ? true : updated;
			updated = updateServerSecurityField(serverDoc, "RestrictedList", username) ? true : updated;
			// Updating AllowAccess breaks all access to the server, including the admin user.  I suspect I need to set additional related fields.
			//updateServerSecurityField(serverDoc, "AllowAccess", server);
			
			// computeWithForm - is this required?
			// This fails with an error like:  
			// [018455:000002-00007FCD65B93700] ECL Alert Result: Code signed by Domino Template Development/Domino was prevented from executing with the right: Access to current database.NotesException: Operation aborted at your request
			//serverDoc.computeWithForm(false, true);
			
			// save
			if (!updated) {
				// If the document is not updated, saving will trigger an error
				System.out.println("No server document updates required.");
			}
			else if (!serverDoc.save(true)) { // force the save
				throw new Exception("Could not update server document.");
			}
			else {
				System.out.println("Server doc has been updated.");
			}
			
			// also update the ACL to give the user access to configure the server.
			updateNamesACL(namesDatabase, username);
		}
		finally {
			if (null != nameObj) { nameObj.recycle();}
			if (null != serverDoc) { serverDoc.recycle();}
			if (null != serverView) { serverView.recycle();}
			if (null != namesDatabase) { namesDatabase.recycle();}
			if (null != session) { session.recycle(); }
			NotesThread.stermThread();
		}
		
	}
	
	/**
	 * Add the given userName to the indicated item in the given document.
	 * Handle duplicates and empty existing values
	 * @param serverDoc  the server document
	 * @param itemName  the name of the item/field
	 * @param userName  the name of the user
	 */
	protected static boolean updateServerSecurityField(Document serverDoc, String itemName, String userName) throws NotesException {
		Vector members = null;
		try {
			
			members = serverDoc.getItemValue(itemName);
			if (null == members || members.size() == 0 ||
			    (members.size() == 1 && members.get(0).toString().trim().isEmpty())) { // default blank entry
				members = new Vector();  // normalize 	
			}
			
			if (!members.contains(userName)) {
				members.add(userName);
				serverDoc.replaceItemValue(itemName, members);
				return true;
			}
			return false;
			
		}
		finally {
			if (null != members) {
				// recycle the vector in case it contains Domino objects
				serverDoc.recycle(members);
			}
		}
		
	}
	
	/**
	 * Add an ACL entry for the user in the given database.
	 * The user will have Manager access with all roles.
	 * This was needed because the user was not properly recognized in AutomaticallyCrossCertifiedUsers, so this could be disabled once that bug is fixed.
	 * @param  database  the database to update.  Expected to be names.nsf
	 * @param  userName  the username to add, in canonical format
	 */
	protected static boolean updateNamesACL(Database database, String userName) throws NotesException, Exception {
		ACL acl = null;
		FileInputStream fis = null;
		boolean updated = false;
		try {
			acl = database.getACL();
			
			String configFile = "/vagrant/dist-support/default_cross_certify_acl.json";
			fis = new FileInputStream(configFile);
			JSONObject json = (JSONObject)new JSONTokener(fis).nextValue();
/* Example JSON:
{
  "level": "manager",
  "type": "person",
  "canDeleteDocuments": true,
  "canReplicateOrCopyDocuments": true,
  "roles": [ 
    "GroupCreator",
    "GroupModifier",
    "NetCreator",
    "PolicyCreator",
    "PolicyModifier",
    "PolicyReader",
    "NetModifier ",
    "ServerCreator",
    "ServerModifier",
    "UserCreator",
    "UserModifier"
  ]
}
*/
			updated = updateACLFromConfig(acl, json, userName);
			
			if (!updated) {
				System.out.println("No ACL updates required.");
			}
			else {
				System.out.println("ACL updated and saved.");
				acl.save();
			}
			
		}
		finally {
			if (null != acl) { acl.recycle(); }
			if (null != fis) { fis.close(); }
		}
		
		return updated;
		

	}
	
	/**
	 * Update the ACL to match the provided configuration.
	 * Adapted from 	https://github.com/DominoGenesis/Genesis/blob/bde62b70bcd0fef35c41117a87daedba4b80a6f6/src/main/java/net/prominic/genesis/JSONRules.java#L534-L704
	 * @param  acl  The names.nsf ACL
	 * @param config  The JSON configuration object
	 */
	private static boolean updateACLFromConfig(ACL acl, JSONObject config, String userName) {
		boolean toSave = false;

		try {
			ACLEntry entry = acl.getEntry(userName);

			// 1. get/create entry (default no access)
			if (entry == null) {
				entry = acl.createACLEntry(userName, ACL.LEVEL_NOACCESS);
				log(String.format("> ACL: new entry (%s)", userName));
				toSave = true;
			}

			// 2. level
			if (config.has("level")) {
				String sLevel = (String) config.get("level");
				int level = ACL.LEVEL_NOACCESS;
				if ("noAccess".equalsIgnoreCase(sLevel)) {
					level = ACL.LEVEL_NOACCESS;
				}
				else if("depositor".equalsIgnoreCase(sLevel)) {
					level = ACL.LEVEL_DEPOSITOR;
				}
				else if("reader".equalsIgnoreCase(sLevel)) {
					level = ACL.LEVEL_READER;
				}
				else if("author".equalsIgnoreCase(sLevel)) {
					level = ACL.LEVEL_AUTHOR;
				}
				else if("editor".equalsIgnoreCase(sLevel)) {
					level = ACL.LEVEL_EDITOR;
				}
				else if("designer".equalsIgnoreCase(sLevel)) {
					level = ACL.LEVEL_DESIGNER;
				}
				else if("manager".equalsIgnoreCase(sLevel)) {
					level = ACL.LEVEL_MANAGER;
				}

				if (entry.getLevel() != level) {
					entry.setLevel(level);
					toSave = true;
					log(String.format(">> ACLEntry: level (%s)", sLevel));
				}
			}

			// 3. type
			if (config.has("type")) {
				String sType = (String) config.get("type");
				int type = ACLEntry.TYPE_UNSPECIFIED;
				if ("unspecified".equalsIgnoreCase(sType)) {
					type = ACLEntry.TYPE_UNSPECIFIED;
				}
				else if("person".equalsIgnoreCase(sType)) {
					type = ACLEntry.TYPE_PERSON;
				}
				else if("server".equalsIgnoreCase(sType)) {
					type = ACLEntry.TYPE_SERVER;
				}
				else if("personGroup".equalsIgnoreCase(sType)) {
					type = ACLEntry.TYPE_PERSON_GROUP;
				}
				else if("serverGroup".equalsIgnoreCase(sType)) {
					type = ACLEntry.TYPE_SERVER_GROUP;
				}
				else if("mixedGroup".equalsIgnoreCase(sType)) {
					type = ACLEntry.TYPE_MIXED_GROUP;
				}

				if (entry.getUserType() != type) {
					entry.setUserType(type);
					log(String.format(">> ACLEntry: type (%s)", sType));
					toSave = true;
				}
			}

			// 4. canCreateDocuments
			boolean canCreateDocuments = config.has("canCreateDocuments") && (Boolean) config.get("canCreateDocuments");
			if (entry.isCanCreateDocuments() != canCreateDocuments) {
				entry.setCanCreateDocuments(canCreateDocuments);
				log(String.format(">> ACLEntry: setCanCreateDocuments (%b)", canCreateDocuments));
				toSave = true;

			}

			// 5. canDeleteDocuments
			boolean canDeleteDocuments = config.has("canDeleteDocuments") && (Boolean) config.get("canDeleteDocuments");
			if (entry.isCanDeleteDocuments() != canDeleteDocuments) {
				entry.setCanDeleteDocuments(canDeleteDocuments);
				log(String.format(">> ACLEntry: canDeleteDocuments (%b)", canDeleteDocuments));
				toSave = true;
			}

			// 6. canCreatePersonalAgent
			boolean canCreatePersonalAgent = config.has("canCreatePersonalAgent") && (Boolean) config.get("canCreatePersonalAgent");
			if (entry.isCanCreatePersonalAgent() != canCreatePersonalAgent) {
				entry.setCanCreatePersonalAgent(canCreatePersonalAgent);
				log(String.format(">> ACLEntry: canCreatePersonalAgent (%b)", canCreatePersonalAgent));
				toSave = true;
			}

			// 7. canCreatePersonalFolder
			boolean canCreatePersonalFolder = config.has("canCreatePersonalFolder") && (Boolean) config.get("canCreatePersonalFolder");
			if (entry.isCanCreatePersonalFolder() != canCreatePersonalFolder) {
				entry.setCanCreatePersonalFolder(canCreatePersonalFolder);
				log(String.format(">> ACLEntry: canCreatePersonalFolder (%b)", canCreatePersonalFolder));
				toSave = true;
			}

			// 8. canCreateSharedFolder
			boolean canCreateSharedFolder = config.has("canCreateSharedFolder") && (Boolean) config.get("canCreateSharedFolder");
			if (entry.isCanCreateSharedFolder() != canCreateSharedFolder) {
				entry.setCanCreateSharedFolder(canCreateSharedFolder);
				log(String.format("> ACL: entry canCreateSharedFolder (%b)", canCreateSharedFolder));
				toSave = true;
			}

			// 9. canCreateLSOrJavaAgent
			boolean canCreateLSOrJavaAgent = config.has("canCreateLSOrJavaAgent") && (Boolean) config.get("canCreateLSOrJavaAgent");
			if (entry.isCanCreateLSOrJavaAgent() != canCreateLSOrJavaAgent) {
				entry.setCanCreateLSOrJavaAgent(canCreateLSOrJavaAgent);
				log(String.format(">> ACLEntry: canCreateLSOrJavaAgent (%b)", canCreateLSOrJavaAgent));
				toSave = true;
			}

			// 10. isPublicReader
			boolean isPublicReader = config.has("isPublicReader") && (Boolean) config.get("isPublicReader");
			if (entry.isPublicReader() != isPublicReader) {
				entry.setPublicReader(isPublicReader);
				log(String.format(">> ACLEntry: isPublicReader (%b)", isPublicReader));
				toSave = true;
			}

			// 11. isPublicWriter
			boolean isPublicWriter = config.has("isPublicWriter") && (Boolean) config.get("isPublicWriter");
			if (entry.isPublicWriter() != isPublicWriter) {
				entry.setPublicWriter(isPublicWriter);
				log(String.format(">> ACLEntry: isPublicWriter (%b)", isPublicWriter));
				toSave = true;
			}

			// 12. canReplicateOrCopyDocuments
			boolean canReplicateOrCopyDocuments = config.has("canReplicateOrCopyDocuments") && (Boolean) config.get("canReplicateOrCopyDocuments");
			if (entry.isCanReplicateOrCopyDocuments() != canReplicateOrCopyDocuments) {
				entry.setCanReplicateOrCopyDocuments(canReplicateOrCopyDocuments);
				log(String.format(">> ACLEntry: canReplicateOrCopyDocuments (%b)", canReplicateOrCopyDocuments));
				toSave = true;
			}

			// 13. roles
			if (config.has("roles")) {
				Vector<?> aclRoles = acl.getRoles();
				log("Valid ACL Roles:  ");
				for (Object aclRole : aclRoles) {
					log("  " + aclRole.toString());
				}
				JSONArray roles = (JSONArray) config.get("roles");
				for (Object roleObj : roles) {
					String role = roleObj.toString();

					if (entry.isRoleEnabled(role)) {
						log(String.format(">> ACLEntry: role already added (%s)", role));
					}
					else if (aclRoles.contains(role)) {
						entry.enableRole(role);
						log(String.format(">> ACLEntry: enableRole (%s)", role));
						toSave = true;
					}
					else {
						log(String.format(">> ACLEntry: ignoring unsupported role (%s)", role));
					}
				}
			}
		} catch (Exception e) {
			log(e);
		}

		return toSave;
	}	
	
	protected static void log(String message) {
		System.out.println(message);
	} 	
	protected static void log(Throwable e) {
		e.printStackTrace(System.out);
	} 	
}
