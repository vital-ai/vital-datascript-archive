package commons.scripts

import java.util.Map;

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus
import ai.vital.vitalservice.query.ResultList

class ZapierStatusScript implements VitalPrimeGroovyScript {

	@Override
	public ResultList executeScript(
			VitalPrimeScriptInterface scriptInterface,
			Map<String, Object> parameters) {

		ResultList rl = new ResultList()
		
		rl.setStatus(VitalStatus.withOKMessage("VitalPrime datascript returned OK"))
		
		return rl;
		
	}

}
