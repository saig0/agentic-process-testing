# agentic-process-testing
Hackathon 2026 project: Agentic process testing


## Process: AI Email Support Agent

> Blueprint: https://marketplace.camunda.com/en-US/apps/522492/ai-email-support-agent
> Readme: https://github.com/camunda/camunda-8-tutorials/tree/main/solutions/bank-ai-loan-approval

Connector secrets:
- CAMUNDA_SAMPLE_AGENT_EMAIL_USERNAME
- CAMUNDA_SAMPLE_AGENT_EMAIL_PASSWORD
- CAMUNDA_SAMPLE_AGENT_EMAIL_IMAP_HOST (new)
- CAMUNDA_SAMPLE_AGENT_EMAIL_IMAP_PORT (new)
- CAMUNDA_SAMPLE_AGENT_EMAIL_SMPT_HOST (new)
  CAMUNDA_SAMPLE_AGENT_EMAIL_SMPT_PORT (new)
- CAMUNDAAGENT_AWS_ACCESS_KEY
- CAMUNDAAGENT_AWS_SECRET_KEY
- CAMUNDAAGENT_DB_BASE_URL (new)
- CAMUNDAAGENT_DB_USERNAME (new)
- CAMUNDAAGENT_DB_PASSWORD (new)

Modifications:
- Email inbound + outbound connector: IMAP details - replace Gmail connection with Connector Secrets, disable encryption
- Vector DB connector: Vector Store - replace AWS with Elasticsearch, set connection details with Connector Secrets

Hacks:
- Email outbound connector: the SMTP port can't be set via Connector Secrets because it requires a number. Hack: hardcode the port in the process to `3025`.

## Local Development

### GreenMail (Mail Server)

Start GreenMail with Docker:

```bash
docker run -t -i -e GREENMAIL_OPTS='-Dgreenmail.setup.test.all -Dgreenmail.auth.disabled -Dgreenmail.hostname=0.0.0.0 -Dgreenmail.users=demo:demo@localhost,agent:agent@localhost -Dgreenmail.users.login=email' -p 3025:3025 -p 3110:3110 -p 3143:3143 -p 3465:3465 -p 3993:3993 -p 3995:3995 -p 8089:8080 greenmail/standalone:2.1.8
````

IMAP: `localhost:3143`
SMTP: `localhost:3025`

Users:
- `demo@localhost` / `demo`
- `agent@localhost` / `agent`

For debugging, you can access the GreenMail web interface (API server) at `http://localhost:8089`.

For troubleshooting, set the option `-Dgreenmail.verbose`.

### Roundcube (Mail Client)

```bash
docker run -d \
  --name roundcube \
  -p 8000:80 \
  --add-host=host.docker.internal:host-gateway \
  -e ROUNDCUBEMAIL_DEFAULT_HOST=host.docker.internal \
  -e ROUNDCUBEMAIL_DEFAULT_PORT=3143 \
  -e ROUNDCUBEMAIL_SMTP_SERVER=host.docker.internal \
  -e ROUNDCUBEMAIL_SMTP_PORT=3025 \
  -e ROUNDCUBEMAIL_DB_TYPE=sqlite \
  roundcube/roundcubemail:latest
```

### Elasticsearch (Vector DB)

> Not required. Instead, you can use the local Elasticsearch instance started by Camunda 8 Run.

Follow the local dev [installation guide](https://www.elastic.co/docs/deploy-manage/deploy/self-managed/local-development-installation-quickstart).
Run the following command to install and start Elasticsearch:

```bash
curl -fsSL https://elastic.co/start-local | sh
``` 

To avoid port conflicts, you modify the `/elastic-start-local/.env` file to change the HTTP port (default is `9200`):

```
ES_LOCAL_PORT=9200
```

Restart Elasticsearch to apply the port change:

```bash
/elastic-start-local/.stop.sh
/elastic-start-local/.start.sh
```

See the connection details after all services are started.

### Camunda 8 Run (8.8.9)

Set connector secrets in `/.env` file:

```
CAMUNDA_SAMPLE_AGENT_EMAIL_USERNAME=agent@localhost
CAMUNDA_SAMPLE_AGENT_EMAIL_PASSWORD=agent
CAMUNDA_SAMPLE_AGENT_EMAIL_ADDRESS=agent@localhost
CAMUNDA_SAMPLE_AGENT_EMAIL_IMAP_HOST=localhost
CAMUNDA_SAMPLE_AGENT_EMAIL_IMAP_PORT=3143
CAMUNDA_SAMPLE_AGENT_EMAIL_SMPT_HOST=localhost
CAMUNDA_SAMPLE_AGENT_EMAIL_SMPT_PORT=3025
CAMUNDAAGENT_DB_BASE_URL=http://localhost:9200
CAMUNDAAGENT_DB_USERNAME=elastic
CAMUNDAAGENT_DB_PASSWORD=chang
CAMUNDAAGENT_AWS_ACCESS_KEY=<Your AWS Access Key/ID>
CAMUNDAAGENT_AWS_SECRET_KEY=<Your AWS Secret Key>
```

Start Camunda 8 Run:

```bash
./start.sh
```

Login to Camunda 8 Run at `http://localhost:8080` with `demo` / `demo`.

## Run example

Deploy the process from Camunda Modeler.

### Send Mail

Using swaks to send test email:

```bash
swaks --to agent@localhost --from demo@localhost --server localhost:3025 --header "Subject: Loan request" --body "Hi, I want to apply for a loan of 15000€ for a new kitchen."
```
