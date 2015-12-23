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
import ai.vital.vitalsigns.model.VitalApp

import com.restfb.Connection;
import com.restfb.DefaultFacebookClient
import com.restfb.FacebookClient.AccessToken
import com.restfb.FacebookClient.DebugTokenError;
import com.restfb.FacebookClient.DebugTokenInfo;
import com.restfb.Parameter;
import com.restfb.types.Page
import com.vitalai.domain.social.Edge_hasFanCountry;
import com.vitalai.domain.social.FacebookAccount
import com.vitalai.domain.social.FanCountry;
import com.restfb.Version
import com.restfb.json.JsonArray;
import com.restfb.json.JsonObject;;

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
				gco.generateURI(scriptInterface != null ? scriptInterface.getApp() : (VitalApp) null)
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
				
			} else if('getCurrentAccount'.equals(action)) {
			
				String accessToken = params.get('accessToken')
				if(!accessToken) throw new Exception("No 'accessToken' param")
				
				Boolean includeAnalytics = params.get('includeAnalytics')
				if(includeAnalytics == null) includeAnalytics = false
				
				DefaultFacebookClient fbClient = new DefaultFacebookClient(accessToken)
				
				Page page = fbClient.fetchObject("me", Page.class, Parameter.with("fields", "name,id,category,username,likes"));

				FacebookAccount fbAcc = new FacebookAccount()
				if(scriptInterface != null) {
					fbAcc.generateURI(scriptInterface.getApp())
				} else {
					fbAcc.generateURI((VitalApp)null)
				}
				fbAcc.accessToken = accessToken
				fbAcc.category = page.getCategory()
				fbAcc.facebookID = page.getId()
				fbAcc.likesCount = page.getLikes() ? page.getLikes().intValue() : null
				fbAcc.name = page.getName()
				fbAcc.pictureURL = 'https://graph.facebook.com/' + page.getId() + '/picture?type=square'
				fbAcc.username = page.getUsername()
				fbAcc.tokenValid = true
				
				rl.results.add(new ResultElement(fbAcc, 1D))
				
				//get analytics
//				Connection<JsonObject> insights = fbClient.fetchConnection("me/insights/page_fans_country", JsonObject.class, Parameter.with("period", "lifetime"))
//				List<JsonObject> l = insights.getData();
//				if(l.size() > 0) {
//					fbAcc.pageFansCountry = l.get(0).toString()
//				}
				//no longer in a property, create nodes 
				
				if(includeAnalytics == true) {
					
					Connection<JsonObject> insights = fbClient.fetchConnection("me/insights/page_fans_country", JsonObject.class, Parameter.with("period", "lifetime"))
							List<JsonObject> l = insights.getData();
					if(l.size() > 0) {
						
						JsonObject analytics = l.get(0);
						JsonArray values = analytics.getJsonArray("values");
						JsonObject val = values.getJsonObject(values.length() - 1).getJsonObject('value');
						for(String countryName : val.keys()) {
							int likesCount = val.getInt(countryName)
									
							FanCountry fc = new FanCountry()
							if(scriptInterface != null) {
								fc.generateURI(scriptInterface.getApp())
							} else {
								fc.generateURI((VitalApp)null)
							}
							fc.name = countryName
							fc.likesCount = likesCount
							
							rl.results.add(new ResultElement(fc, 1D))
							
							Edge_hasFanCountry edge = new Edge_hasFanCountry().addSource(fbAcc).addDestination(fc)
							if(scriptInterface != null) {
								edge.generateURI(scriptInterface.getApp())
							} else {
								edge.generateURI((VitalApp)null)
							}						
							
							rl.results.add(new ResultElement(edge, 1D))
							
						}
						
					}
				}
				
				
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
