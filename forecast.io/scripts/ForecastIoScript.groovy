package commons.scripts

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper
import java.text.DecimalFormat
import java.util.Map

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalsigns.model.VITAL_GraphContainerObject;;;
import ai.vital.vitalsigns.model.VitalApp

class ForecastIoScript implements VitalPrimeGroovyScript {

	private static HttpClient httpClient = new HttpClient()
	
	private static DecimalFormat decimalFormat = new DecimalFormat('0.000000')
	
	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> params) {
	
		
		ResultList rl = new ResultList()
		
		try {
		
			Double lat = params.lat
			if(!lat) throw new Exception("No lat param")
			
			Double lng = params.lng
			if(!lng) throw new Exception("No lng param")
			
			String apiKey = params.apiKey
			if(!apiKey) throw new Exception("No apiKey param")
			
			GetMethod getMethod2 = new GetMethod("https://api.forecast.io/forecast/${apiKey}/${decimalFormat.format(lat)},${decimalFormat.format(lng)}")
			
			String responseBody2 = ""
			
			int statusCode2 = 0
			try {
				statusCode2 = httpClient.executeMethod(getMethod2)
					
				try {
					responseBody2 = getMethod2.getResponseBodyAsString()
				} catch(Exception e) {
				}
					
			} finally {
				getMethod2.releaseConnection()
			}
				
			
			if(statusCode2 < 200 || statusCode2 > 299) {
				throw new Exception("forecast.io HTTP error: ${statusCode2} - ${responseBody2}")
			}
			
			Map parsed = new JsonSlurper().parseText(responseBody2)
			
			TimeZone tz = TimeZone.getTimeZone(parsed.timezone)
			
			List dailyData = parsed.daily.data;
			
			GregorianCalendar gcal = new GregorianCalendar(tz)
			
			for(int i = 0 ; i < dailyData.size(); i++) {
				
				Map daily = dailyData[i]
				gcal.setTimeInMillis( (long) daily.time * 1000L )
				daily.dayOfWeek = gcal.get(GregorianCalendar.DAY_OF_WEEK) 
				
			}
			
			VITAL_GraphContainerObject r = new VITAL_GraphContainerObject().generateURI((VitalApp) null)			
			r.jsonData = JsonOutput.toJson(parsed)
			
			rl.addResult(r, 1d)
						
		} catch(Exception e) {
			e.printStackTrace()
			rl.status = VitalStatus.withError(e.localizedMessage)
		}
		
		return rl;
	}

}
