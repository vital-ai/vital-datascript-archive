package commons.scripts

import groovy.json.JsonSlurper

import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.GetMethod

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalsigns.model.VITAL_GraphContainerObject
import ai.vital.vitalsigns.model.VitalApp

class GoogleDirectionsSearchScript implements VitalPrimeGroovyScript {

	private static HttpClient httpClient = new HttpClient()
	
	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> params) {

		ResultList rl = new ResultList()
		
		try {
		
			String startAddress = params.startAddress
			if(!startAddress) throw new Exception("No startAddress param")
			
			String endAddress = params.endAddress
			if(!endAddress) throw new Exception("No endAddress param")
			
			GetMethod getMethod = new GetMethod('https://maps.googleapis.com/maps/api/directions/json?origin=' + URLEncoder.encode(startAddress, 'UTF-8') + '&destination=' + URLEncoder.encode(endAddress, 'UTF-8'))
			
			
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
					
				throw new Exception("directions api HTTP error: ${statusCode} - ${responseBody}")
					
			}
			
			
			JsonSlurper slurper = new JsonSlurper()
			
			Map respObject = slurper.parseText(responseBody)
			
			String status = respObject.status
			
			if(! status.equalsIgnoreCase("OK")) {
				throw new Exception("Directions service error: " + status + ' - ' + respObject.error_message)
			}
			
			VITAL_GraphContainerObject r = new VITAL_GraphContainerObject().generateURI((VitalApp) null)
			r.directionsJson = responseBody
			
			rl.addResult(r)
			
		} catch(Exception e) {
			e.printStackTrace()
			rl.status = VitalStatus.withError(e.localizedMessage)
		}
		
		return rl;
	}

}
