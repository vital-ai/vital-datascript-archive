package commons.scripts

import java.util.Map

import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.methods.PostMethod;
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.query.ResultElement
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalsigns.model.VITAL_GraphContainerObject
import ai.vital.vitalsigns.model.VitalApp
import com.vitalai.domain.social.SoundCloudAccount
import groovy.json.JsonSlurper;

class SoundCloudApiScript implements VitalPrimeGroovyScript {

	private final static Logger log = LoggerFactory.getLogger(SoundCloudApiScript.class)
	
	private static HttpClient httpClient = new HttpClient()
	
	static String baseURL = 'https://api.soundcloud.com'
	
	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> params) {

		ResultList rl = new ResultList()
		
		try {
			
			String action = params.get('action')
			if(!action) throw new Exception("No 'action' param")
			
			if('convertSoundCloudAccessCode'.equals(action)) {
				
				
				//https://developers.soundcloud.com/docs/api/reference#token
				
				String clientID = params.get('clientID')
				if(!clientID) throw new Exception("No clientID param")
				
				String clientSecret = params.get('clientSecret')
				if(!clientSecret) throw new Exception("No clientSecret param")
				
				String redirectURI = params.get('redirectURI')
				if(!redirectURI) throw new Exception("No 'redirectURI' param")
				
				String code = params.get('code')
				if(!code) throw new Exception("No 'code' param")
				
				if(code.endsWith('#')) {
					code = code.substring(0, code.length() - 1)
				}
				
				
				PostMethod postMethod = new PostMethod(baseURL + "/oauth2/token")
				
				String accessToken = null
				
				try {
					
					postMethod.setRequestBody([
			           new NameValuePair('client_id', clientID ),
			           new NameValuePair('client_secret', clientSecret),
			           new NameValuePair('redirect_uri', redirectURI),
			           new NameValuePair('grant_type', 'authorization_code'), // (authorization_code, refresh_token, password, client_credentials, oauth1_token)
			           new NameValuePair('code', code)
			        ] as NameValuePair[])

					int status = httpClient.executeMethod(postMethod)
					
					String body = "(empty response)"
					
					try { body = postMethod.getResponseBodyAsString(10000) } catch(Exception e) {} 
					
//					log.info("SoundCloud API response: ${body}")
					
					if(status < 200 || status > 299) {
						throw new Exception("SoundCloud API error: ${status} - ${body}")
					}
					
					Map parsed = null
					
					try {
						parsed = (Map) new JsonSlurper().parseText(body)

						accessToken = parsed.get('access_token')
						
						if(!accessToken) throw new Exception("No 'access_token' field")			
									
//						parsed = 
							
					} catch(Exception e) {
						throw new Exception("SoundCloud API response parsing error: ${e.localizedMessage}, body: ${body}")
					}
										
					
				} finally {
				
					postMethod.releaseConnection()
				
				}
				
				
				SoundCloudAccount account = getCurrentAccount(scriptInterface, accessToken)
				rl.results.add(new ResultElement(account, 1D)) 
				
			} else if('validateAccessToken'.equals(action)) {
			
				String accessToken = params.get('accessToken')
				if(!accessToken) throw new Exception("No accessToken param")
				
				
				VITAL_GraphContainerObject gco = new VITAL_GraphContainerObject()
				gco.generateURI(scriptInterface != null ? scriptInterface.getApp() : (VitalApp) null)
				
				try {
					SoundCloudAccount account = getCurrentAccount(scriptInterface, accessToken)
					gco.setProperty("valid", true)
				} catch(Exception e) {
					gco.setProperty("valid", false)
					gco.setProperty("error", e.localizedMessage)
				}
				
				rl.results.add(new ResultElement(gco, 1D))
				
			} else if('getCurrentAccount'.equals(action)) {
			
				String accessToken = params.get('accessToken')
				if(!accessToken) throw new Exception("No accessToken param")
				
				
				SoundCloudAccount sca = getCurrentAccount(scriptInterface, accessToken)
				
				rl.results.add(new ResultElement(sca, 1D))
				
			} else {
			
				throw new Exception("Unknown action: ${action}")
			
			}
			
			
		} catch(Exception e) {
		
			rl.status = VitalStatus.withError(e.localizedMessage)
		
		}
		
		
		return rl;
	}
	
	
	SoundCloudAccount getCurrentAccount(VitalPrimeScriptInterface scriptInterface, String accessToken) {
		
		GetMethod getMethod = new GetMethod(baseURL + "/me?oauth_token=" + accessToken )
		
//		"https://api.soundcloud.com/me?oauth_token=A_VALID_TOKEN"
		
		try {
			
			int status = httpClient.executeMethod(getMethod)
			
			String body = "(empty response)"
			
			try { body = getMethod.getResponseBodyAsString(10000) } catch(Exception e) {}
			
			if(status < 200 || status > 299) {
				throw new Exception("SoundCloud API error: ${status} - ${body}")
			}
			
			Map parsed = null
			
			try {
				parsed = (Map) new JsonSlurper().parseText(body)

			} catch(Exception e) {
				throw new Exception("SoundCloud API response parsing error: ${e.localizedMessage}, body: ${body}")
			}

			SoundCloudAccount a = new SoundCloudAccount()
			a.generateURI(scriptInterface != null ? scriptInterface.getApp() : (VitalApp) null)
			a.soundCloudID = parsed.id
			a.permalink = parsed.permalink
			a.username = parsed.username
			a.soundCloudURI = parsed.uri
			a.permalinkURL = parsed.permalink_url
			a.pictureURL = parsed.avatar_url
			a.country = parsed.country
			a.name = parsed.full_name
			a.city = parsed.city
			a.description = parsed.description
			a.discogsName = parsed.discogs_name
			a.myspaceName = parsed.myspace_name
			a.website = parsed.website
			a.websiteTitle = parsed.website_title
			a.tracksCount = parsed.track_count  
			a.playlistsCount = parsed.playlist_count
			a.followersCount = parsed.followers_count
			a.followingCount = parsed.followings_count
			a.favoriteCount = parsed.public_favorites_count
			
			a.accessToken = accessToken
			
			return a		
//			{
//				"id": 3207,
//				"permalink": "jwagener",
//				"username": "Johannes Wagener",
//				"uri": "https://api.soundcloud.com/users/3207",
//				"permalink_url": "http://soundcloud.com/jwagener",
//				"avatar_url": "http://i1.sndcdn.com/avatars-000001552142-pbw8yd-large.jpg?142a848",
//				"country": "Germany",
//				"full_name": "Johannes Wagener",
//				"city": "Berlin",
//				"description": "<b>Hacker at SoundCloud</b>\r\n\r\nSome of my recent Hacks:\r\n\r\nsoundiverse.com \r\nbrowse recordings with the FiRe app by artwork\r\n\r\ntopbillin.com \r\nfind people to follow on SoundCloud\r\n\r\nchatter.fm \r\nget your account hooked up with a voicebox\r\n\r\nrecbutton.com \r\nrecord straight to your soundcloud account",
//				"discogs_name": null,
//				"myspace_name": null,
//				"website": "http://johannes.wagener.cc",
//				"website_title": "johannes.wagener.cc",
//				"online": true,
//				"track_count": 12,
//				"playlist_count": 1,
//				"followers_count": 416,
//				"followings_count": 174,
//				"public_favorites_count": 26,
//				"plan": "Pro Plus",
//				"private_tracks_count": 63,
//				"private_playlists_count": 3,
//				"primary_email_confirmed": true
//			  }
			
		} finally {
		
			getMethod.releaseConnection()
		
		}
		
	}

}
