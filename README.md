# agentic-process-testing

Camunda Hackathon 2026 project: Agentic process testing.

## The process: AI Email Support Agent

Based on the Camunda blueprint:

- [Marketplace: Blueprint](https://marketplace.camunda.com/en-US/apps/522492/ai-email-support-agent)
- [Readme](https://github.com/camunda/camunda-8-tutorials/tree/main/solutions/bank-ai-loan-approval)

Connector secrets:

- CAMUNDAAGENT_AWS_ACCESS_KEY
- CAMUNDAAGENT_AWS_SECRET_KEY
- CAMUNDA_SAMPLE_AGENT_EMAIL_USERNAME
- CAMUNDA_SAMPLE_AGENT_EMAIL_PASSWORD
- CAMUNDA_SAMPLE_AGENT_EMAIL_IMAP_HOST (new)
- CAMUNDA_SAMPLE_AGENT_EMAIL_IMAP_PORT (new)
- CAMUNDA_SAMPLE_AGENT_EMAIL_SMPT_HOST (new)
  CAMUNDA_SAMPLE_AGENT_EMAIL_SMPT_PORT (new)
- CAMUNDAAGENT_DB_BASE_URL (new)
- CAMUNDAAGENT_DB_USERNAME (new)
- CAMUNDAAGENT_DB_PASSWORD (new)

Modifications:

- Email inbound connector: IMAP details - Replace Gmail connection with Connector Secrets, disable encryption
- Email outbound connector: SMPT details - Replace Gmail connection with Connector Secrets, disable encryption
- Vector DB connector: Vector Store - Replace AWS with Elasticsearch, set connection details with Connector Secrets

Hacks:

- Email outbound connector: the SMTP port can't be set via Connector Secrets because it requires a number. Hack:
  hardcode the port in the process to `3025`.

## Local Development

You need a mail server, a mail client, and Camunda 8 Run to run the process locally. 

### Mail Server and Client

- Mail server: [GreenMail](https://github.com/greenmail-mail-test/greenmail) 
- Mail client: [Roundcube](https://roundcube.net/) 

Start GreenMail and Roundcube using the docker-compose file `docker-compose.yml`.

IMAP server: `localhost:3143`
SMTP server: `localhost:3025`

Users:

- `demo@camunda.com` / `demo`
- `agent@camunda.com` / `agent`

Go to http://localhost:8000/ to access Roundcube webmail client. Login with `demo@camunda.com` / `demo`.

For debugging, you can access the GreenMail web interface (API server) at `http://localhost:8089`. 

### Elasticsearch (Vector DB)

> Not required. Instead, you can use the local Elasticsearch instance started by Camunda 8 Run.

Follow the local
dev [installation guide](https://www.elastic.co/docs/deploy-manage/deploy/self-managed/local-development-installation-quickstart).
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

### Camunda 8 Run 

Download [Camunda 8 Run](https://developers.camunda.com/install-camunda-8/) (8.8.9)

Set connector secrets in `/.env` file:

```
CAMUNDAAGENT_AWS_ACCESS_KEY=<Your AWS Access Key/ID>
CAMUNDAAGENT_AWS_SECRET_KEY=<Your AWS Secret Key>
CAMUNDA_SAMPLE_AGENT_EMAIL_IMAP_HOST=localhost
CAMUNDA_SAMPLE_AGENT_EMAIL_IMAP_PORT=3143
CAMUNDA_SAMPLE_AGENT_EMAIL_SMPT_HOST=localhost
CAMUNDA_SAMPLE_AGENT_EMAIL_SMPT_PORT=3025
CAMUNDA_SAMPLE_AGENT_EMAIL_USERNAME=agent@camunda.com
CAMUNDA_SAMPLE_AGENT_EMAIL_PASSWORD=agent
CAMUNDA_SAMPLE_AGENT_EMAIL_ADDRESS=agent@camunda.com
CAMUNDAAGENT_DB_BASE_URL=http://localhost:9200
CAMUNDAAGENT_DB_USERNAME=elastic
CAMUNDAAGENT_DB_PASSWORD=changeme
```

Start Camunda 8 Run:

```bash
./start.sh
```

Login to Camunda 8 Run at `http://localhost:8080` with `demo` / `demo`.

## Run the process

Deploy the process and all user forms from Camunda Modeler.

Open the webmail client at http://localhost:8000/ and login with `demo@camunda.com` / `demo`.

Send a new email:

- From: `demo@camunda.com`
- To: `agent@camunda.com`
- Topic: "Loan request"
- Message: "Hi, I want to apply for a loan to renovate my house." 
