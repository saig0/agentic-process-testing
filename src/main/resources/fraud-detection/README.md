# Fraud Detection Process Example

This example demonstrates how to deploy and run an AI-driven fraud detection process in Camunda 8, where an AI agent analyzes
a tax submission.

---

## Prerequisites

- **Camunda 8.8+** (SaaS or Self-Managed)
- Access to Camunda Connectors (Agentic AI, HTTP, etc.)
- Outbound internet access for connectors (to reach APIs)
- Mail server

---

## Secrets & Configuration

This example requires AWS Bedrock and Email server access. You need to set up the following credentials. Create the following secrets in your Camunda cluster:

- `AWS_BEDROCK_ACCESS_KEY` - AWS Bedrock access key 
- `AWS_BEDROCK_SECRET_KEY` - AWS Bedrock secret key 
- `CAMUNDA_SAMPLE_AGENT_EMAIL_USERNAME`
- `CAMUNDA_SAMPLE_AGENT_EMAIL_PASSWORD`
- `CAMUNDA_SAMPLE_AGENT_EMAIL_IMAP_HOST`
- `CAMUNDA_SAMPLE_AGENT_EMAIL_IMAP_PORT`
- `CAMUNDA_SAMPLE_AGENT_EMAIL_SMPT_HOST`
- `CAMUNDA_SAMPLE_AGENT_EMAIL_SMPT_PORT`

Configure the connectors in the Web Modeler or via environment variables as needed.

---

## Local Development

You need a mail server, a mail client, and Camunda 8 Run to run the process locally.

### Mail Server and Client

- Mail server: [GreenMail](https://github.com/greenmail-mail-test/greenmail)
- Mail client: [Roundcube](https://roundcube.net/)

Start GreenMail and Roundcube using the docker-compose file [docker-compose.yml](docker-compose.yml).

- IMAP server: `localhost:3143`
- SMTP server: `localhost:3025`

Users:

- `demo@camunda.com` / `demo`
- `agent@camunda.com` / `agent`

Go to http://localhost:8000/ to access Roundcube webmail client. Login with `demo@camunda.com` / `demo`.

For debugging, you can access the GreenMail web interface (API server) at http://localhost:8089.

### Camunda 8 Run

Download [Camunda 8 Run](https://developers.camunda.com/install-camunda-8/) (8.8.9)

Set connector secrets in `/.env` file:

```
AWS_BEDROCK_ACCESS_KEY=<Your AWS Access Key/ID>
AWS_BEDROCK_SECRET_KEY=<Your AWS Secret Key>
CAMUNDA_SAMPLE_AGENT_EMAIL_IMAP_HOST=localhost
CAMUNDA_SAMPLE_AGENT_EMAIL_IMAP_PORT=3143
CAMUNDA_SAMPLE_AGENT_EMAIL_SMPT_HOST=localhost
CAMUNDA_SAMPLE_AGENT_EMAIL_SMPT_PORT=3025
CAMUNDA_SAMPLE_AGENT_EMAIL_USERNAME=agent@camunda.com
CAMUNDA_SAMPLE_AGENT_EMAIL_PASSWORD=agent
CAMUNDA_SAMPLE_AGENT_EMAIL_ADDRESS=agent@camunda.com
```

Start Camunda 8 Run:

```bash
./start.sh
```

Login to Camunda 8 Run at http://localhost:8080 with `demo` / `demo`.

## How to Deploy & Run

1. **Deploy the BPMN Model**
	- Open Camunda Desktop/Web Modeler.
	- Deploy the process `fraud-detection-process.bpmn` and all the form files from this folder.

2. **Start a New Instance**
	- Use Tasklist to start an instance by filling out the form to start an instance.
   
3. **Interact**
	- Use Operate to monitor the process instance.
    - Use Tasklist to complete any user tasks.
    - Check the email inbox and send a reply. Open the mail client at http://localhost:8000/ and login with `demo@camunda.com` / `demo`.

_Made with ❤️ by Camunda_
