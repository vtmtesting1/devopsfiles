kubectl patch rs spin-echo-v000 -n spinnaker -p '{ "spec": { "template": { "spec": { "containers": [{ "name": "spin-echo", "livenessProbe": { "failureThreshold": 3, "httpGet": { "path": "/health", "port": 8089, "scheme": "HTTP" }, "initialDelaySeconds": 60, "periodSeconds": 10, "successThreshold": 1, "timeoutSeconds": 3 }}]}}}}'

