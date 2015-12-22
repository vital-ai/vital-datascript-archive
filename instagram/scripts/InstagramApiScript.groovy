package commons.scripts

import java.util.Map

import org.jinstagram.Instagram
import org.jinstagram.auth.InstagramAuthService;
import org.jinstagram.auth.model.Token
import org.jinstagram.auth.model.Verifier;
import org.jinstagram.auth.oauth.InstagramService
import org.jinstagram.entity.users.basicinfo.UserInfo
import org.jinstagram.entity.users.basicinfo.UserInfoData;
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.vitalai.domain.social.InstagramAccount;

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.query.ResultElement
import ai.vital.vitalservice.query.ResultList;;
import ai.vital.vitalsigns.model.VITAL_GraphContainerObject
import ai.vital.vitalsigns.model.VitalApp

class InstagramApiScript implements VitalPrimeGroovyScript {

	private final static Logger log = LoggerFactory.getLogger(InstagramApiScript.class)
	
	private static final Token EMPTY_TOKEN = null;
	
	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> params) {

		ResultList rl = new ResultList()
		
		try {
			
			String action = params.get('action')
			if(!action) throw new Exception("No 'action' param")
			
			if('convertInstagramAccessCode'.equals(action)) {
				
				String clientID = params.get('clientID')
				if(!clientID) throw new Exception("No clientID param")
				
				String clientSecret = params.get('clientSecret')
				if(!clientSecret) throw new Exception("No clientSecret param")
				
				String redirectURI = params.get('redirectURI')
				if(!redirectURI) throw new Exception("No 'redirectURI' param")
				
				String code = params.get('code')
				
				InstagramService authService = new InstagramAuthService()
					.apiKey(clientID)
					.apiSecret(clientSecret)
					.callback(redirectURI)
					.build()
					
				Token accessToken = authService.getAccessToken(EMPTY_TOKEN, new Verifier(code))
				
				Instagram instagram = new Instagram(accessToken);
				
				InstagramAccount ia = new InstagramAccount()
				ia.generateURI(scriptInterface != null ? scriptInterface.getApp() : (VitalApp) null)
				ia.accessToken = accessToken.getToken()
				
				UserInfo userInfo = instagram.getCurrentUserInfo()
				UserInfoData userInfoData = userInfo.getData()
				ia.bio = userInfoData.getBio()
				ia.instagramID = userInfoData.getId()
				ia.name = userInfoData.getFullName()
				ia.pictureURL = userInfoData.getProfilePicture()
				ia.username = userInfoData.getUsername()
				ia.website = userInfoData.getWebsite()
				
				rl.results.add(new ResultElement(ia, 1D))
				
			} else if('validateAccessToken'.equals(action)) {
			
				String accessToken = params.get('accessToken')
				if(!accessToken) throw new Exception("No accessToken param")
			
				Instagram instagram = new Instagram(accessToken, (String) null)
				
				VITAL_GraphContainerObject gco = new VITAL_GraphContainerObject()
				gco.generateURI(scriptInterface.getApp())
				
				try {
					
					UserInfo userInfo = instagram.getCurrentUserInfo()
					if(userInfo == null || userInfo.getData() == null) throw new Exception("No user data");
					gco.setProperty("valid", true)
					
				} catch(Exception e) {
					gco.setProperty("valid", false)
					gco.setProperty("error", e.localizedMessage)
				}
			
				rl.results.add(new ResultElement(gco, 1D))
			
			} else if('getCurrentAccount'.equals(action)) {
			
				String accessToken = params.get('accessToken')
				if(!accessToken) throw new Exception("No accessToken param")
			
				Instagram instagram = new Instagram(accessToken, (String) null)
			
				InstagramAccount ia = new InstagramAccount()
				ia.generateURI(scriptInterface != null ? scriptInterface.getApp() : (VitalApp) null)
				ia.accessToken = accessToken
				
				UserInfo userInfo = instagram.getCurrentUserInfo()
				UserInfoData userInfoData = userInfo.getData()
				ia.bio = userInfoData.getBio()
				ia.instagramID = userInfoData.getId()
				ia.name = userInfoData.getFullName()
				ia.pictureURL = userInfoData.getProfilePicture()
				ia.username = userInfoData.getUsername()
				ia.website = userInfoData.getWebsite()
				
				rl.results.add(new ResultElement(ia, 1D))
				
			} else {
			
				throw new Exception("Unknown action: ${action}")
			
			}
			
			
		} catch(Exception e) {
		
			rl.status = VitalStatus.withError(e.localizedMessage)
		
		}
		
		
		return rl;
	}

}
