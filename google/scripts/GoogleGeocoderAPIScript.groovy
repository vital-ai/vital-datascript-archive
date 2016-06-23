package commons.scripts

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory;

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalsigns.model.VITAL_GraphContainerObject;
import ai.vital.vitalsigns.model.VitalApp

import com.google.code.geocoder.Geocoder
import com.google.code.geocoder.GeocoderRequestBuilder
import com.google.code.geocoder.model.GeocodeResponse
import com.google.code.geocoder.model.GeocoderAddressComponent
import com.google.code.geocoder.model.GeocoderComponent;
import com.google.code.geocoder.model.GeocoderRequest
import com.google.code.geocoder.model.GeocoderResult;
import com.google.code.geocoder.model.GeocoderStatus;

class GoogleGeocoderAPIScript implements VitalPrimeGroovyScript {

	private final static Logger log = LoggerFactory.getLogger(GoogleGeocoderAPIScript.class)
	
	static Geocoder publicGeocoder = new Geocoder();
	
	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> params) {

		ResultList rl = new ResultList()
		
		try {
		
			String action = params.get('action')
			if(!action) throw new Exception("No 'action' parameter")
			
			
			String clientID = params.get('clientID')
			String clientKey = params.get('clientKey')
			
			Geocoder geocoder = null
			
			if(clientID && clientKey) {
				geocoder = new Geocoder(clientID, clientKey)	
			} else {
				geocoder = publicGeocoder
			}
			
			GeocoderRequestBuilder requestBuilder = new GeocoderRequestBuilder()
			if(action == 'geocodeZipcode') {

				String countryCode = params.get('countryCode')
				if(!countryCode) throw new Exception("No 'countryCode' param")
				
				String zipCode = params.get('zipCode')
				if(!zipCode) throw new Exception("No 'zipCode' param")

				requestBuilder.setRegion(countryCode)
				requestBuilder.setAddress(zipCode)				
				//this library does not handle components well - complains about lack of location or address whereas it should
				//accept just components
//				requestBuilder.addComponent(GeocoderComponent.COUNTRY, countryCode)
//				.addComponent(GeocoderComponent.POSTAL_CODE, zipCode)
								
			} else {
			
				throw new Exception("Unknown action: " + action)
			
			}
			
			GeocoderRequest request = requestBuilder.getGeocoderRequest()
			
			GeocodeResponse response = publicGeocoder.geocode(request)
			
			GeocoderStatus status = response.getStatus()
			
			
			if(status == GeocoderStatus.OK) {
				
				for( GeocoderResult result : response.getResults() ) {
					
					VITAL_GraphContainerObject r = new VITAL_GraphContainerObject()
					r.mainResult = true
					r.generateURI((VitalApp) null)
					r.formattedAddress = result.getFormattedAddress()
					r.partialMatch = result.isPartialMatch()
					if( result.types ) {
						
						r.resultTypes = result.types.join(', ')
						
					}
					
					rl.addResult(r)
					
					for(GeocoderAddressComponent c : result.getAddressComponents()) {
						
						VITAL_GraphContainerObject sub = new VITAL_GraphContainerObject()
								sub.generateURI((VitalApp) null)
						sub.mainResult = false
						sub.longName = c.getLongName()
						sub.shortName = c.getShortName()
						if( c.types ) {
							sub.componentTypes = c.types.join(", ")
						}
					
						rl.addResult(sub)
							
					}
					
				}
				
				
			} else {
			
				throw new Exception("Geocoder error: " + status.name())
			
			}
			
			
				
		} catch(Exception e) {
			log.error(e.getLocalizedMessage(), e)
			rl.status = VitalStatus.withError(e.localizedMessage)
		}
		
		return rl;
	}

}
