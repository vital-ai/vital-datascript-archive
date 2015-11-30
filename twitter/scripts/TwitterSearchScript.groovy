package commons.scripts

import java.util.Map;

import com.vitalai.domain.social.Tweet;

import commons.scripts.HaleySpeechToText.ResultElement;

import twitter4j.GeoLocation;
import twitter4j.Query
import twitter4j.Query.ResultType;
import twitter4j.Query.Unit;
import twitter4j.auth.OAuth2Token;
import twitter4j.conf.ConfigurationBuilder;
import twitter4j.QueryResult;
import twitter4j.Status;
import twitter4j.Twitter
import twitter4j.TwitterFactory
import ai.vital.prime.groovy.VitalPrimeGroovyScript;
import ai.vital.prime.groovy.VitalPrimeScriptInterface;
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.query.ResultList;
import ai.vital.vitalsigns.model.VitalApp;
import ai.vital.vitalservice.query.ResultElement

class TwitterSearchScript implements VitalPrimeGroovyScript {

	
	static synchronized Twitter getTwitter(String consumerKey, String consumerSecret) {
		
			
		ConfigurationBuilder builder = new ConfigurationBuilder()
			.setApplicationOnlyAuthEnabled(true)	
			.setOAuthConsumerKey(consumerKey)
			.setOAuthConsumerSecret(consumerSecret)
			
		TwitterFactory twitterFactory = new TwitterFactory(builder.build())
		Twitter twitter = twitterFactory.getInstance()
//		twitter.setOAuthConsumer(consumerKey, consumerSecret)
		OAuth2Token token = twitter.getOAuth2Token();
		return twitter
		
	}
	
	@Override
	public ResultList executeScript(
			VitalPrimeScriptInterface scriptInterface,
			Map<String, Object> parameters) {

		ResultList rl = new ResultList()
		
		try {

			String type = parameters.get('searchType')
			if(!type) throw new Exception("No searchType param")
			
			String key = parameters.get('key')
			if(!key) throw new Exception('No key param')
			String secret = parameters.get('secret')
			if(!secret) throw new Exception('No secret param')
			
			Twitter twitter = getTwitter(key, secret)
			
			if(type == 'tweets') {
				
				Query q = new Query()

				Integer _count = parameters.get('count')
				
				if(_count != null) { q.setCount(_count) }
				
				Map geoCode = parameters.get('geoCode')
				
				if(geoCode != null) {
					
					Double lat = geoCode.lat
					Double lon = geoCode.lon
					
					Double radius = geoCode.radius
					
					Unit unit = Unit.valueOf(geoCode.unit)
					
					GeoLocation geoLocation = new GeoLocation(lat, lon);
					
					q.setGeoCode(geoLocation, radius, unit)
					
				}
				
				String lang = parameters.get('lang')
				
				if(lang) { q.setLang(lang) }

				String locale = parameters.get('locale')
				if(locale) { q.setLocale(locale) }
				
				Long maxId = parameters.get('maxId')
				if(maxId != null) q.setMaxId(maxId)
				
				String query = parameters.get('query')
				if(query) { q.setQuery(query) }
				
				String resultType = parameters.get('resultType')
				ResultType rt = ResultType.mixed
				
				if(resultType) {
					rt = ResultType.valueOf(resultType)
				}
				q.setResultType(rt)
				
				String since = parameters.get('since')
				if(since) q.setSince(since)
				
				Long sinceId = parameters.get('sinceId')
				if(sinceId != null) { q.setSinceId(sinceId) }
				
				String until = parameters.get('until')
				if(until) { q.setUntil(until) }
								
				QueryResult qr = twitter.search(q);
				
				rl.setTotalResults(qr.getCount())
				
				for(Status tweet : qr.getTweets()) {
					
					Tweet tw = new Tweet()
					tw.generateURI((VitalApp) null)
					tw.body = tweet.getText()
					tw.publicationDate = tweet.createdAt
					tw.lang = tweet.lang
					tw.tweetID = tweet.id
					tw.sourceName = tweet.source
					tw.authorID = tweet.user.id
					tw.authorName = tweet.user.name 
					
					rl.results.add(new ResultElement(tw, 1D))
//					tweet.
					
				}
				
			} else if(type == 'places' || type == 'users') {
			
				throw new RuntimeException('places and users search not implemented')
			
//				twitter.searchPlaces(?
				
			} else {
				throw new RuntimeException("Unknown searchType value: ${type}")
			}
			
						
			
			
			
		} catch(Exception e) {
			rl.status = VitalStatus.withError("Twitter search failed: ${e.localizedMessage}")
		}

		return rl;
	}
			
			

}
