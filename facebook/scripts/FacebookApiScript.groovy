package commons.scripts

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory;

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus
import ai.vital.vitalservice.query.ResultElement;
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalsigns.model.VITAL_GraphContainerObject;

import com.restfb.DefaultFacebookClient
import com.restfb.FacebookClient.AccessToken
import com.restfb.FacebookClient.DebugTokenError;
import com.restfb.FacebookClient.DebugTokenInfo;
import com.restfb.Parameter;
import com.restfb.types.Page
import com.restfb.Version;

class FacebookApiScript implements VitalPrimeGroovyScript {

	private final static Logger log = LoggerFactory.getLogger(FacebookApiScript.class)
	
	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> params) {

		ResultList rl = new ResultList()
		
		try {
		
			String action = params.get('action')
			if(!action) throw new Exception("No 'action' param")
			
			if('getLongLivedFacebookPageAccessToken'.equals(action)) {
				
				String appID = params.get('appID')
				if(!appID) throw new Exception("No appID param")
				
				String appSecret = params.get('appSecret')
				if(!appSecret) throw new Exception("No appSecret param")
				
				String userAccessToken = params.get('userAccessToken')
				if(!userAccessToken) throw new Exception("No userAccessToken param")
				
				String pageID = params.get('pageID')
				if(!pageID) throw new Exception("No pageID param")
				
				AccessToken extendedToken = new DefaultFacebookClient(Version.VERSION_2_5).obtainExtendedAccessToken(appID, appSecret, userAccessToken)
				
				log.info("Extended Access token: ${maskPassword(extendedToken.getAccessToken())}, type ${extendedToken.getTokenType()} expires: ${extendedToken.getExpires()}")
		
				def fbClient = new DefaultFacebookClient(extendedToken.getAccessToken())
				
				Page page = fbClient.fetchObject(pageID, Page.class, Parameter.with("fields", "access_token"))
				
				String newPageAccessToken = page.getAccessToken()
				
				log.info("New pageAccessToken: ${maskPassword(newPageAccessToken)}")
				
				
				VITAL_GraphContainerObject r = new VITAL_GraphContainerObject()
				r.generateURI(scriptInterface.getApp())
				r.setProperty('pageAccessToken', newPageAccessToken)
				
				//the accounts should now have never expiring tokens
						
				rl.results.add(new ResultElement(r, 1D))
				
			} else if('validateAccessToken'.equals(action)) {
			
				String appToken = params.get('appToken')
				if(!appToken) throw new Exception("No 'appToken' param")
				
				String accessToken = params.get('accessToken')
				if(!accessToken) throw new Exception("No 'accessToken' param")
				
				
				def fbClient = new DefaultFacebookClient(appToken, Version.VERSION_2_5)
				
				DebugTokenInfo info = fbClient.debugToken(accessToken)
				
				VITAL_GraphContainerObject gco = new VITAL_GraphContainerObject()
				gco.generateURI(scriptInterface.getApp())
				gco.setProperty("valid", info.isValid())
				gco.setProperty("expiresAt", info.getExpiresAt())
				if(!info.isValid()) {
					DebugTokenError error = null
					try { error = info.error } catch(Exception e) { }
					if(error != null) {
						gco.setProperty("errorCode", "" + error.code)
						gco.setProperty("errorSubcode", "" + error.subcode)
						gco.setProperty("error", error.message)
					} else {
						gco.setProperty("error", "unknown error")
					}
				}
				
				rl.results.add(new ResultElement(gco, 1D))
				
			} else {
				throw new Exception("Unknown action: ${action}")
			}
				
		} catch(Exception e) {
		
			rl.status = VitalStatus.withError(e.localizedMessage)
			
		}
		
		return rl;
	}
	
	String maskPassword(String n) {
		
		if(n == null) return 'null'
		
		String output = "";

		for( int i = 0 ; i < n.length(); i++) {
				
			if(n.length() < 8 || ( i >= 4 && i < (n.length() - 4) ) ) {
				output += '*'
			} else {
				output += n.substring(i, i+1)
			}
				
		}
		
		return output
		
	}

}
