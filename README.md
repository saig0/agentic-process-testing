# agentic-process-testing
Hackathon 2026 project: Agentic process testing


## Process: AI Email Support Agent

Connector secrets:
- CAMUNDA_SAMPLE_AGENT_EMAIL_IMAP_HOST
- CAMUNDA_SAMPLE_AGENT_EMAIL_IMAP_PORT
- CAMUNDA_SAMPLE_AGENT_EMAIL_USERNAME
- CAMUNDA_SAMPLE_AGENT_EMAIL_PASSWORD

Modification:
- Email inbound connector: IMAP details - replace Gmail connection with Connector Secrets, disable encryption

## Local Development

### GreenMail

Start GreenMail with Docker:

```bash
docker run -t -i -e GREENMAIL_OPTS='-Dgreenmail.setup.test.all -Dgreenmail.auth.disabled -Dgreenmail.hostname=0.0.0.0 -Dgreenmail.users=demo:demo@localhost,agent:agent@localhost -Dgreenmail.users.login=email' -p 3025:3025 -p 3110:3110 -p 3143:3143 -p 3465:3465 -p 3993:3993 -p 3995:3995 -p 8089:8080 greenmail/standalone:2.1.8
````

IMAP: `localhost:3143`

Users:
- `demo@localhost` / `demo`
- `agent@localhost` / `agent`

For debugging, you can access the GreenMail web interface (API server) at `http://localhost:8089`.

For troubleshooting, set the option `-Dgreenmail.verbose`.

### Camunda 8 Run (8.8.9)

Set connector secrets in `/.env` file:

```
CAMUNDA_SAMPLE_AGENT_EMAIL_IMAP_HOST=localhost
CAMUNDA_SAMPLE_AGENT_EMAIL_IMAP_PORT=3143
CAMUNDA_SAMPLE_AGENT_EMAIL_USERNAME=agent@localhost
CAMUNDA_SAMPLE_AGENT_EMAIL_PASSWORD=agent
```

Start Camunda 8 Run:

```bash
./start.sh
```

Login to Camunda 8 Run at `http://localhost:8080` with `demo` / `demo`.

Deploy the process from Camunda Modeler.

### Send Mail

Using swaks to send test email:

```bash
swaks --to agent@localhost --from demo@localhost --server localhost:3025 --header "Subject: Loan request" --body "Hi, I want to apply for a loan of 15000€ for a new kitchen."
```
