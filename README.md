# vital-datascript-archive

The datascripts modules are organized in the following manner:
    
    <module_name>/bin/...
    <module_name>/jobs/...
    <module_name>/scripts/...
    <module_name>/libs/...

The scripts directory contains groovy files that are meant to be copied into vitalprime into service commons scripts directory:
```$VITAL_HOME/vital-config/vitalprime/scripts/commons/scripts/```

The jobs are set up per application thus they should be copied and config updated in the app specific directory: 
```$VITAL_HOME/vital-config/vitalprime/scripts/<organizationID>/<appID>/```

The libs should be copied into ```$VITAL_HOME/vitalprime/lib-datascripts/``` directory

'bin' directory is supposed to contain groovy env scripts to test a deployed datascript remotely
