package commons.scripts

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.GetMethod

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalsigns.model.VITAL_GraphContainerObject
import ai.vital.vitalsigns.model.VitalApp

class GoogleMapsSearchScript implements VitalPrimeGroovyScript {

	private static HttpClient httpClient = new HttpClient()
	
	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> params) {

		ResultList rl = new ResultList()
		
		try {
			
			String query = params.query
			if(!query) throw new Exception("No query param")
			
			GetMethod getMethod = new GetMethod('https://maps.googleapis.com/maps/api/geocode/json?address=' + URLEncoder.encode(query, 'UTF-8'))// + '&key=' + URLEncoder.encode(q, 'UTF-8'))
			
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
					
				throw new Exception("geocoder HTTP error: ${statusCode} - ${responseBody}")
					
			}
			
			JsonSlurper slurper = new JsonSlurper()
			
			Map respObject = slurper.parseText(responseBody)
			
			List results = respObject.results
			
			String status = respObject.status
			
			if(! status.equalsIgnoreCase("OK")) {
				throw new Exception("Geocoding service error: " + status + ' - ' + respObject.error_message)
			}
			
			
			for(Map res : results) {
				
				String address = res.formatted_address;
				
				VITAL_GraphContainerObject location = new VITAL_GraphContainerObject().generateURI((VitalApp) null)
				
				location.jsonData = JsonOutput.toJson(res)
				
				rl.addResult(location, 1d)
					
			}
			
		} catch(Exception e) {
			e.printStackTrace()
			rl.status = VitalStatus.withError(e.localizedMessage)
		}
		
		return rl;
	}

}
