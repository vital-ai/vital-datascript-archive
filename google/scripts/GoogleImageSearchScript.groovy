package commons.scripts

import groovy.json.JsonSlurper

import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.GetMethod

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalsigns.model.VITAL_GraphContainerObject

class GoogleImageSearchScript implements VitalPrimeGroovyScript {

	private static HttpClient httpClient = new HttpClient()
	
	private static String googleCustomSearchURLTemplate = 'https://www.googleapis.com/customsearch/v1?'
	
	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> params) {

		ResultList rl = new ResultList()
		
		try {
			
			String googleCustomSearchAPIKey = params.googleCustomSearchAPIKey
			if(!googleCustomSearchAPIKey) {throw new Exception("No 'googleCustomSearchAPIKey' param, required for image search") }
			
			String googleCustomSearchEngineID = params.googleCustomSearchEngineID
			if(!googleCustomSearchEngineID) { throw new Exception("No 'googleCustomSearchEngineID' param, required for image search") }
			
			String query = params.query
			if(!query) { throw new Exception("No 'query' param") }
			
			String referer = params.referer
		
			String url = googleCustomSearchURLTemplate + "key=" + googleCustomSearchAPIKey + "&cx=" + googleCustomSearchEngineID + "&searchType=image&q=" + URLEncoder.encode(query, 'UTF-8')
		
			GetMethod getMethod = new GetMethod(url)
			if(referer) {
				getMethod.addRequestHeader("Referer", referer)
			}
		
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
		
			for(Map res : items) {
			
				Map img = res.image
			
				VITAL_GraphContainerObject imgRef = new VITAL_GraphContainerObject()
				
				imgRef.URI = res.link//res.unescapedUrl
				imgRef.thumbnailLink = img.thumbnailLink //img.tbUrl)
				imgRef.thumbnailHeight = img.thumbnailHeight
				imgRef.thumbnailWidth = img.thumbnailWidth
				imgRef.title = filterGoogleText(res.title)
				imgRef.contextLink = img.contextLink //img.originalContextUrl
				
				rl.addResult(imgRef, 1d)
				
			}
			
		} catch(Exception e) {
			rl.status = VitalStatus.withError(e.getLocalizedMessage())
		}
		
		return rl;
		
	
	}
	
	static String filterGoogleText(String input) {
		if(input == null) return input
		return input.replace('&#39;', "'")
	}

}
