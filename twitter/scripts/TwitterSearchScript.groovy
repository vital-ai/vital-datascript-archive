package commons.scripts

import java.util.Map;

import com.vitalai.domain.social.Tweet;

import commons.scripts.HaleySpeechToText.ResultElement;

import twitter4j.GeoLocation
import twitter4j.Paging;
import twitter4j.Query
import twitter4j.Query.ResultType;
import twitter4j.Query.Unit;
import twitter4j.auth.OAuth2Token;
import twitter4j.conf.ConfigurationBuilder;
import twitter4j.QueryResult
import twitter4j.ResponseList;
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
	
	
	static Tweet statusToTweet(Status tweet) {
	
		Tweet tw = new Tweet()
		tw.generateURI((VitalApp) null)
		tw.body = tweet.getText()
		tw.publicationDate = tweet.createdAt
		tw.lang = tweet.lang
		tw.tweetID = tweet.id
		tw.sourceName = tweet.source
		tw.authorID = tweet.user.id
		tw.authorName = tweet.user.name
		
		tw.retweet = tweet.isRetweet()
		
		Status originalTweet = tweet.getRetweetedStatus()
		if(originalTweet != null) {
			tw.originalAuthorID = originalTweet.user?.id
			tw.originalAuthorName = originalTweet.user?.name
			tw.originalAuthorScreenName = originalTweet.user?.screenName
		}
		
		if( tweet.inReplyToScreenName != null ) tw.inReplyToScreenName = tweet.inReplyToScreenName
		if( tweet.inReplyToStatusId > 0L) tw.inReplyToTweetID = tweet.inReplyToStatusId  
		if( tweet.inReplyToUserId > 0L) tw.inReplyToUserID = tweet.inReplyToUserId
		
		GeoLocation geoLocation = tweet.getGeoLocation()
		if(geoLocation != null) {
			tw.latitude = geoLocation.latitude
			tw.longitude = geoLocation.longitude
		}

		return tw		
			
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
					
					Tweet tw = statusToTweet(tweet)
					
					rl.results.add(new ResultElement(tw, 1D))
//					tweet.
					
				}
				
				
			} else if(type == 'user_timeline') {
			
				Long userId = parameters.userId
				String screenName = parameters.screenName

				if(userId == null && screenName == null) {
					throw new Exception("One of userId nor screenName required")
				} else if(userId != null && screenName != null) {
					throw new Exception("Expected exactly one of userId or screenName, cannot handle both")
				}
				
				Integer _count = parameters.get('count')
				
				Paging p = new Paging()
				if(_count != null) {
					p.setCount(_count)
				}

				ResponseList<Status> tweets = null
				if(userId != null) {
					tweets = twitter.getUserTimeline(userId, p)
				} else {
					tweets = twitter.getUserTimeline(screenName, p)
				}
				
				for(Status tweet : tweets) {
					
					Tweet tw = statusToTweet(tweet)
					
					rl.results.add(new ResultElement(tw, 1D))
//					tweet.
					
				}

				
								
//				Example Values: 12345
//				
//				screen_name
//				optional
//				The screen name of the user for whom to return results for.
//				
//				Example Values: noradio
//				
//				since_id
//				optional
//				Returns results with an ID greater than (that is, more recent than) the specified ID. There are limits to the number of Tweets which can be accessed through the API. If the limit of Tweets has occured since the since_id, the since_id will be forced to the oldest ID available.
//				
//				Example Values: 12345
//				
//				count
//				optional
//				Specifies the number of tweets to try and retrieve, up to a maximum of 200 per distinct request. The value of count is best thought of as a limit to the number of tweets to return because suspended or deleted content is removed after the count has been applied. We include retweets in the count, even if include_rts is not supplied. It is recommended you always send include_rts=1 when using this API method.
//				
//				max_id
//				optional
//				Returns results with an ID less than (that is, older than) or equal to the specified ID.
//				
//				Example Values: 54321
//				
//				trim_user
//				optional
//				When set to either true, t or 1, each tweet returned in a timeline will include a user object including only the status authors numerical ID. Omit this parameter to receive the complete user object.
//				
//				Example Values: true
//				
//				exclude_replies
//				optional
//				This parameter will prevent replies from appearing in the returned timeline. Using exclude_replies with the count parameter will mean you will receive up-to count tweets — this is because the count parameter retrieves that many tweets before filtering out retweets and replies. This parameter is only supported for JSON and XML responses.
//				
//				Example Values: true
//				
//				contributor_details
//				optional
//				This parameter enhances the contributors element of the status response to include the screen_name of the contributor. By default only the user_id of the contributor is included.
//				
//				Example Values: true
//				
//				include_rts
//				optional
//				When set to false, the timeline will strip any native retweets (though they will still count toward both the maximal length of the timeline and the slice selected by the count parameter). Note: If you’re using the trim_user parameter in conjunction with include_rts, the retweets will still contain a full user object.
//				
//				Example Values: false
			
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
