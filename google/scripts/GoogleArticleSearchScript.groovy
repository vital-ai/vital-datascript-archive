package commons.scripts

import groovy.json.JsonSlurper
import java.text.SimpleDateFormat
import java.util.Map

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalsigns.model.VITAL_GraphContainerObject
import ai.vital.vitalsigns.model.VitalApp;;;;

class GoogleArticleSearchScript implements VitalPrimeGroovyScript {

	private static String googleCustomSearchURLTemplate = 'https://www.googleapis.com/customsearch/v1?'
	
	private static SimpleDateFormat newsSearchEngineDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
	
	private static HttpClient httpClient = new HttpClient()
	
	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> params) {

		ResultList rl = new ResultList()
		
		try {
			
			String googleCustomSearchAPIKey = params.googleCustomSearchAPIKey
			if(!googleCustomSearchAPIKey) { throw new Exception("No 'googleCustomSearchAPIKey' param, required for image search") }
			
			String googleNewsSearchEngineID = params.googleNewsSearchEngineID
			if(!googleNewsSearchEngineID) { throw new Exception("No 'googleNewsSearchEngineID' param, required for image search") }

			String query = params.query
			if(!query) { throw new Exception("No 'query' param") }
						
			String url = googleCustomSearchURLTemplate + "key=" + googleCustomSearchAPIKey + "&cx=" + googleNewsSearchEngineID + "&q=" + URLEncoder.encode(query, 'UTF-8')
			
			String referer = params.referer
			
			GetMethod getMethod = new GetMethod(url)
			getMethod.addRequestHeader("Referer", "https://haley.vital.ai")
			
			int statusCode = 0
			
			String responseBody = ""
			
			try {
			
				statusCode = httpClient.executeMethod(getMethod)
				
				
				try {
				
					responseBody = getMethod.getResponseBodyAsString()
						
				} catch(Exception e) {
				}
				
			} finally {
				getMethod.releaseConnection()
			}
			
			if(statusCode < 200 || statusCode > 299) {
					
				throw new Exception("image api HTTP error: ${statusCode} - ${responseBody}")
					
			}
			
			JsonSlurper slurper = new JsonSlurper()
			
			Map respObject = slurper.parseText(responseBody)
			
			List items = respObject.items
			
			int i = 0
			
			for(Map res : items) {

				i++
				
				if(i > 1) break
				
				VITAL_GraphContainerObject article = new VITAL_GraphContainerObject().generateURI((VitalApp) null)
								
				article.title = filterGoogleText(res.title)
	//			article.publicationDate = newsDateFormat.parse(res.publishedDate)
				article.body = filterGoogleText( res.snippet )
				article.url = res.link
				
				Map pagemap = res.pagemap
				
				//publication date taken from pagemap data
				List metatags = res.metatags
				
				if(metatags != null && metatags.size() > 0) {
					Map mt = metatags[0]
					String publishDate = mt.get('cxenseparse:recs:publishtime')
					
					try {
						if(publishDate) {
							article.publicationDate = newsSearchEngineDateFormat.parse(publishDate)
						}
					} catch(Exception e) {
					}
					
					//TODO other ways to find publication date
				}
				
				
				List cse_thumbnail = pagemap.cse_thumbnail
				
				List imageObjects = pagemap.imageobject
				
				List cse_image = pagemap.cse_image
				
				if(cse_thumbnail != null && ( cse_thumbnail.size() > 0 && imageObjects != null && imageObjects.size() > 0) || (cse_image != null && cse_image.size() > 0) ) {
					
					Map img = cse_thumbnail[0]
					
					Map imgObj = imageObjects ? imageObjects[0] : null
					
					Map cseImg = cse_image ? cse_image[0] : null

					article.imageURL1 =cseImg? cseImg.src : (imgObj  ? imgObj.url : null )
					article.imageURL = img.src
					article.heightPx = img.height ? Integer.parseInt(img.height) : null
					article.widthPx = img.width ? Integer.parseInt(img.width) : null
					article.imageTitle = imgObj ? filterGoogleText(imgObj.name) : null
							
				}
				
				rl.addResult(article, 1d)
				
			}
			
		} catch(Exception e) {
			e.printStackTrace()
			rl.status = VitalStatus.withError(e.localizedMessage)
		}
		
		return rl

	}
	
	
	static String filterGoogleText(String input) {
		if(input == null) return input
		return input.replace('&#39;', "'")
	}

}
