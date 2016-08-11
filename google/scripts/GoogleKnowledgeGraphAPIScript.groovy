package commons.scripts

import java.util.Map

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalsigns.model.VITAL_GraphContainerObject;;;
import ai.vital.vitalsigns.model.VitalApp

class GoogleKnowledgeGraphAPIScript implements VitalPrimeGroovyScript {

	static HttpClient httpClient = new HttpClient()
	
	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> parameters) {

		ResultList rl = new ResultList()
		
		GetMethod getMethod = null
		
		try {
			
			String apiKey = parameters.get('apiKey')
			if(!apiKey) throw new Exception("No apiKey param")
			
			String query = parameters.get('query')
			
			Integer limit = 1
			
			if(parameters.get('limit' ) != null) {
				limit = parameters.get('limit')
				if(limit.intValue() <= 0) throw new Exception("Limit must be > 0 - ${limit}")
			}
			
			
			String url = "https://kgsearch.googleapis.com/v1/entities:search?query=${URLEncoder.encode(query, 'UTF-8')}&key=${apiKey}&limit=${limit}&indent=true"
			
			getMethod = new GetMethod(url)
			
			int status = httpClient.executeMethod(getMethod)
			String body = ''
			try {
				body = getMethod.getResponseBodyAsString()
			} catch(Exception e) {}
			
			if(status != 200) throw new Exception("HTTP status ${status} - ${body}")
			
			VITAL_GraphContainerObject obj = new VITAL_GraphContainerObject().generateURI((VitalApp) null)
			obj.jsonData = body
			
			rl.addResult(obj) 
			 
		} catch(Exception e) {
			rl.status = VitalStatus.withError(e.localizedMessage) 
			
		} finally {
		
			if(getMethod != null) getMethod.releaseConnection()
		
		}
		
		
		return rl;
	}

	
	
}
