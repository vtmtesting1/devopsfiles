kubectl patch rs spin-front50-v000 -n spinnaker -p '{ "spec": { "template": { "spec": { "containers": [{ "name": "spin-front50", "livenessProbe": { "failureThreshold": 3, "httpGet": { "path": "/health", "port": 8080, "scheme": "HTTP" }, "initialDelaySeconds": 60, "periodSeconds": 10, "successThreshold": 1, "timeoutSeconds": 3 }}]}}}}'

