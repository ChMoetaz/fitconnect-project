{{- define "fitconnect.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "fitconnect.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "fitconnect.postgres.fullname" -}}{{ include "fitconnect.fullname" . }}-postgres{{- end -}}
{{- define "fitconnect.backend.fullname" -}}{{ include "fitconnect.fullname" . }}-backend{{- end -}}
{{- define "fitconnect.frontend.fullname" -}}{{ include "fitconnect.fullname" . }}-frontend{{- end -}}

{{- define "fitconnect.labels" -}}
app.kubernetes.io/name: {{ include "fitconnect.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end -}}

{{- define "fitconnect.datasourceUrl" -}}
jdbc:postgresql://{{ include "fitconnect.postgres.fullname" . }}:5432/{{ .Values.postgres.auth.database }}
{{- end -}}
