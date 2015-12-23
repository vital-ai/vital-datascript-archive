package commons.scripts

import java.util.Map;
import org.slf4j.Logger
import org.slf4j.LoggerFactory;

import twitter4j.Twitter
import twitter4j.TwitterException;
import twitter4j.TwitterFactory
import twitter4j.User
import twitter4j.auth.AccessToken
import twitter4j.auth.RequestToken

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.query.ResultElement
import ai.vital.vitalservice.query.ResultList;;
import ai.vital.vitalsigns.model.VITAL_GraphContainerObject
import com.vitalai.domain.social.TwitterAccount

class TwitterApiScript implements VitalPrimeGroovyScript {

	private final static Logger log = LoggerFactory.getLogger(TwitterApiScript.class)
	
	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> params) {

		ResultList rl = new ResultList()
		
		try {
			
			String action = params.get('action')
			if(!action) throw new Exception("No 'action' param")
			
			String apiKey = params.get('apiKey')
			if(!apiKey) throw new Exception("No apiKey param")
			
			String apiSecret = params.get('apiSecret')
			if(!apiSecret) throw new Exception("No apiSecret param")
			
			if('generateOAuthToken'.equals(action)) {
				
				Twitter twitter = new TwitterFactory().getInstance()
				twitter.setOAuthConsumer(apiKey, apiSecret)
					
				RequestToken requestToken = twitter.getOAuthRequestToken()
				
//				requestTokensCache.put(requestToken.getToken(), requestToken)
				
				log.info "Twitter request token: ${requestToken.getToken()} secret: ${maskPassword(requestToken.getTokenSecret())}, url: ${requestToken.getAuthorizationURL()}"
				
				VITAL_GraphContainerObject gco = new VITAL_GraphContainerObject()
				gco.generateURI(scriptInterface.getApp())
				gco.setProperty("token", requestToken.getToken())
				gco.setProperty("tokenSecret", requestToken.getTokenSecret())
				gco.setProperty("authorizationURL", requestToken.getAuthorizationURL())
				
				rl.results.add(new ResultElement(gco, 1D))
				
			} else if('convertTwitterRequestToken'.equals(action)) {
			
				String token = params.get('token')
				if(!token) throw new Exception("No token param")
				
				String tokenSecret = params.get('tokenSecret')
				if(!tokenSecret) throw new Exception("No tokenSecret param")
				
				String oauth_token = params.get('oauth_token')
				if(!oauth_token) throw new Exception("No oauth_token param")
				
				String oauth_verifier = params.get('oauth_verifier');
				if(!oauth_verifier) throw new Exception("No oauth_verifier param")
			
				
				//not from 
				RequestToken rToken = new RequestToken(token, tokenSecret)
			
				Twitter twitter = new TwitterFactory().getInstance()
				twitter.setOAuthConsumer(apiKey, apiSecret)
			
				AccessToken accessToken = twitter.getOAuthAccessToken(rToken, oauth_verifier)
				twitter.setOAuthAccessToken(accessToken)
			
				User user = twitter.users().verifyCredentials();
			
				TwitterAccount tw = new TwitterAccount()
				tw.generateURI(scriptInterface.getApp())
				tw.description = user.getDescription()
				tw.followersCount = user.getFollowersCount()
				tw.followingCount = user.getFriendsCount()
				tw.likesCount = user.getFavouritesCount()
				tw.name = user.getName()
				tw.oAuthToken = accessToken.getToken()
				tw.oAuthTokenSecret = accessToken.getTokenSecret()
				tw.pictureURL = user.getProfileImageURLHttps()
				tw.screenName = user.getScreenName()
				tw.tweetsCount = user.getStatusesCount()
				tw.twitterID = user.getId()
				tw.tokenValid = true
			
				rl.results.add(new ResultElement(tw, 1D))
			
			} else if('validateAccessToken'.equals(action)) {
			
				Twitter twitter = new TwitterFactory().getInstance()
				twitter.setOAuthConsumer(apiKey, apiSecret)
				
				String oAuthToken = params.get('oAuthToken')
				if(!oAuthToken) throw new Exception("No oAuthToken param")
				
				String oAuthTokenSecret = params.get('oAuthTokenSecret')
				if(!oAuthTokenSecret) throw new Exception("No oAuthTokenSecret param")
				
				AccessToken accessToken = new AccessToken(oAuthToken, oAuthTokenSecret)
				twitter.setOAuthAccessToken(accessToken)
				
				VITAL_GraphContainerObject gco = new VITAL_GraphContainerObject()
				gco.generateURI(scriptInterface.getApp())
				
				try {
					
					User user = twitter.users().verifyCredentials();
					gco.setProperty("valid", true)
					
				} catch(TwitterException e) {
				
					gco.setProperty("valid", false)
					gco.setProperty("error", e.localizedMessage)
				
				}
				
				rl.results.add(new ResultElement(gco, 1D))
				
			} else if('getCurrentAccount'.equals(action)) {
			
				Twitter twitter = new TwitterFactory().getInstance()
				twitter.setOAuthConsumer(apiKey, apiSecret)
				
				String oAuthToken = params.get('oAuthToken')
				if(!oAuthToken) throw new Exception("No oAuthToken param")
				
				String oAuthTokenSecret = params.get('oAuthTokenSecret')
				if(!oAuthTokenSecret) throw new Exception("No oAuthTokenSecret param")
				
				AccessToken accessToken = new AccessToken(oAuthToken, oAuthTokenSecret)
				twitter.setOAuthAccessToken(accessToken)
				
				User user = twitter.users().verifyCredentials();
				
				TwitterAccount tw = new TwitterAccount()
				tw.generateURI(scriptInterface.getApp())
				tw.description = user.getDescription()
				tw.followersCount = user.getFollowersCount()
				tw.followingCount = user.getFriendsCount()
				tw.likesCount = user.getFavouritesCount()
				tw.name = user.getName()
				tw.oAuthToken = accessToken.getToken()
				tw.oAuthTokenSecret = accessToken.getTokenSecret()
				tw.pictureURL = user.getProfileImageURLHttps()
				tw.screenName = user.getScreenName()
				tw.tweetsCount = user.getStatusesCount()
				tw.twitterID = user.getId()
				tw.tokenValid = true
				
				rl.results.add(new ResultElement(tw, 1D))
				
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
