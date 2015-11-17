package commons.scripts

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import groovy.json.JsonSlurper;

import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.io.IOUtils;

import com.vitalai.domain.social.YouTubeComment;

import ai.vital.prime.groovy.v2.VitalPrimeGroovyScriptV2
import ai.vital.prime.groovy.v2.VitalPrimeScriptInterfaceV2
import ai.vital.vitalservice.VitalStatus
import ai.vital.vitalservice.query.ResultElement
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalsigns.model.VitalApp;


class YouTubeCommentsScript implements VitalPrimeGroovyScriptV2 {

	private static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
	static {
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
	}
	
	private static HttpClient client = new HttpClient()
	
	@Override
	public ResultList executeScript(
			VitalPrimeScriptInterfaceV2 scriptInterface,
			Map<String, Object> parameters) {
			
		ResultList rl = new ResultList()
		
		InputStream inputStream = null
		
		GetMethod getMethod = null
		
		try {
			
//			// Authorize the request.
//			Credential credential = Auth.authorize(scopes, "commentthreads");
//
//			// This object is used to make YouTube Data API requests.
//			youtube = new YouTube.Builder(Auth.HTTP_TRANSPORT, Auth.JSON_FACTORY, credential)
//					.setApplicationName("youtube-cmdline-commentthreads-sample").build();
//
//			String part = 
	
			String textFormat = parameters.get('textFormat')
			if(!textFormat) textFormat = 'plainText'
					
			String videoId = parameters.get('videoId')
			if(!videoId) throw new RuntimeException("No 'videoId' param")
			
			Integer max = parameters.get('max')
			if(max != null) {
				if(max <= 0) throw new RuntimeException("'max' must be > 0")	
			} else {
				max = 20
			}
			

			//optional date range filter			
			Date minDate = parameters.get('minDate')
			Date maxDate = parameters.get('maxDate')
			
			String parts = parameters.get('parts')
			if(!parts) throw new RuntimeException("No 'parts' param") 
			
			String key = parameters.get('key')
			if(!key) throw new RuntimeException("No 'key' param")
			
			
			boolean keepSearching = true
			
			String nextPageToken = null
			
			while(keepSearching) {
				
				String url = "https://www.googleapis.com/youtube/v3/commentThreads?part=${es(parts)}&videoId=${es(videoId)}&maxResults=100&key=${es(key)}"
				
				if(nextPageToken) {
					url += "&pageToken=${es(nextPageToken)}"
				}
				
				//ripped from Google API explorer: AIzaSyCFj15TpkchL4OUhLD1Q2zgxQnMb7v3XaM
				getMethod = new GetMethod(url)
				
				int status = client.executeMethod(getMethod)
				if(status != 200) {
					String resp = ""
							try { resp = getMethod.getResponseBodyAsString(10000) } catch(Exception e) {}
					throw new Exception("YouTube API HTTP status: ${status} - ${resp}")
				}
				
				inputStream = getMethod.getResponseBodyAsStream()
						
				CommentsPage page = extractComments(inputStream, minDate, maxDate)
				
				inputStream.close()
				getMethod.releaseConnection()
				
				nextPageToken = page.nextPage
				
				for( YouTubeComment c : page.comments ) {
					
					rl.results.add(new ResultElement(c, 1D))
					
					if(rl.results.size() == max) {
						keepSearching = false
						break
					}
					
				}
				
				if(nextPageToken == null) {
					//no more results
					keepSearching = false
				}
				
				
			}
			
			
		} catch(Exception e) {
			rl.status = VitalStatus.withError("YouTube search failed: ${e.localizedMessage}")
		} finally {
			IOUtils.closeQuietly(inputStream)
			if(getMethod != null) {
				getMethod.releaseConnection()
			}
		}

		return rl;
		
	}
			
	static class CommentsPage {
		String nextPage
		List<YouTubeComment> comments = []
	}
			
	static CommentsPage extractComments(InputStream inputStream, Date minDate, Date maxDate) {
		
		JsonSlurper slurper = new JsonSlurper()
		Map res = slurper.parse(inputStream)
		
		CommentsPage p = new CommentsPage()
		p.nextPage = res.nextPageToken
		
		for(Map item : res.items) {
			
			Map mainSnippet = item.snippet
			
			Map tlc = mainSnippet.topLevelComment

			YouTubeComment comment = toComment(tlc, minDate, maxDate)
			
			if(comment != null) p.comments.add(comment)
			
			Map repliesMap = item.replies
			
			if(!repliesMap) continue
			
			List comments = repliesMap.comments 
			
			if(!comments) continue
			
			for(Map c : comments) {
				
				YouTubeComment r = toComment(c, minDate, maxDate)
				
				if(r != null) p.comments.add(r)
				
			}

		}
		
		return p
		
		
	}
	
	static YouTubeComment toComment(Map c, Date minDate, Date maxDate) {
		
		Map snippet = c.snippet

		Date d = dateFormat.parse(snippet.publishedAt)
		
		if(minDate != null &&  d.getTime() < minDate.getTime()) {
			return null
		}		
		
		if(maxDate != null && d.getTime() > maxDate.getTime()) {
			return null
		}
		
		YouTubeComment comment = new YouTubeComment().generateURI((VitalApp)null)
		comment.body = snippet.textDisplay
		comment.authorName = snippet.authorDisplayName
		comment.channelID = snippet.channelId
		comment.videoID = snippet.videoId
		comment.likeCount = snippet.likeCount
		comment.publicationDate = d
		comment.commentID = c.id
		
		return comment
		
	}
			
	static String es(String i) {
		return URLEncoder.encode(i, 'UTF-8')
	}

}
