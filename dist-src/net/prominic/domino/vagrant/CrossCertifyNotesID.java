package net.prominic.domino.vagrant;

import lotus.domino.*;

import java.io.FileInputStream;

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
			
			crossCertify(targetID, userPassword, server, certID, certPassword);
				
			System.out.println( "crossCertifyNotesID() completed.");
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
	 */
	public static void crossCertify(String targetID, String userPassword, String server, String certID, String certPassword)  throws Exception {
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
			if (reg.crossCertify(targetID, 
				certPassword, // certifier password
				"programmatically cross certified")) // comment field
			{ 
				System.out.println("Recertification succeeded"); 
			}
			else { 
				System.out.println("Recertification failed");
			}
		} catch(NotesException e) {
			System.out.println( e.id + " " + e.text);
			e.printStackTrace();
		}
		finally {
			NotesThread.stermThread();
		}	
	}

	 	
}
