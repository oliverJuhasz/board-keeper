# Running Board Keeper yourself

Board Keeper runs as two containers: the application, which serves both the API and the web app, and a PostgreSQL database. This guide sets them up with Docker Compose, and covers the first Admin, updates, backups and getting back in when the only Admin has forgotten their password.

How your installation is reached from outside (your network, a reverse proxy, a certificate), when it is updated and when it is backed up are yours to decide. This guide says what Board Keeper needs from them, not how to set them up.

## What you need

- A machine that runs Docker with Docker Compose (the `docker compose` command). Board Keeper and PostgreSQL together use about 400 MB of memory when idle; leave them 1 GB.
- An address that people reach Board Keeper at over **https**, such as `https://boardkeeper.example.com`. Board Keeper works over plain http too, but browsers allow passkeys and installing it as an app only over https (or on `localhost`), and the sign-in cookie is marked secure only then.

## Starting it

1. Make a folder for it, and put two files from this repository's `deploy/` folder in it: `compose.yaml` and `.env.example`.
2. Rename `.env.example` to `.env` and fill it in:
   - `POSTGRES_PASSWORD`: a long random password for the database, for example the output of `openssl rand -base64 32`. You never type it anywhere else.
   - `BOARDKEEPER_URL`: the https address people open, without a path. Leave it out and Board Keeper works without passkeys.
   - `BOARD_KEEPER_PORT`: the port on this machine that Board Keeper answers on, 8080 unless you change it.
3. Start it:

   ```bash
   docker compose up -d
   ```

4. Board Keeper has no Accounts yet, so it writes a one-time link for creating the first Admin to its log:

   ```bash
   docker compose logs app
   ```

   Look for a line like `/setup?token=…`, add it to your address (`https://boardkeeper.example.com/setup?token=…`) and open it. Choose the first Admin's username, password and name there. The link works once; a new one is written each time Board Keeper starts, until the first Admin exists.

5. Invite the others from the **Players** page: add a Player for each person, then "Invite" gives a link to send them. Board Keeper sends no email.

## Behind a reverse proxy

Board Keeper expects a reverse proxy in front of it to handle https: the proxy holds the certificate and passes the requests on to Board Keeper's port. Any proxy will do (Caddy, nginx, Traefik, or the one built into a NAS). Board Keeper needs this from it:

- Pass every path through, unchanged. The web app, the API (`/api/…`) and the health check (`/actuator/health`) all live at the same address.
- Send the `X-Forwarded-Proto` header (and preferably `X-Forwarded-Host` and `X-Forwarded-For`), so Board Keeper knows the visitor came in over https. Most proxies do this by default.
- Pass the `Authorization` header through, for programs that use an API Key. Most proxies do this by default.
- Nothing else is needed: no WebSockets, no sticky sessions.

Set `BOARDKEEPER_URL` to the address the proxy answers at, exactly as people type it, and start Board Keeper again with `docker compose up -d`.

## Updating

Board Keeper is published as container images. `BOARD_KEEPER_IMAGE` in `.env` says which one runs; left out, it is `latest`, the newest release. To update to it:

```bash
docker compose pull
docker compose up -d
```

Board Keeper updates its database by itself when it starts. Sessions survive, so nobody is signed out. Phones and browsers that have Board Keeper open show "A new version of Board Keeper is ready" with a Reload button.

Every image has a tag, the part after the colon in `ghcr.io/oliverjuhasz/board-keeper:latest`:

| Tag | What it is |
|---|---|
| `latest` | The newest release. |
| `1.2.3` | One release. It never changes. |
| `snapshot` | The newest change, released or not. It is usually ahead of `latest`, and less tried. |
| `sha-1a2b3c4` | One change, named after its commit. It never changes. |

To run something other than the newest release, set `BOARD_KEEPER_IMAGE` in `.env`, for example to `ghcr.io/oliverjuhasz/board-keeper:1.2.3`, and run the two commands above.

**About Board Keeper**, in the account menu, shows what is running. A release shows its number. Anything else shows as a Snapshot, with its `sha-…` tag and how many changes it is past the newest release.

What changed in each release is in [`CHANGELOG.md`](../CHANGELOG.md). When the first number of a version goes up, as from 1.4.0 to 2.0.0, updating needs something from you, such as a setting that was renamed: read the changelog first.

Going back to an older version than the one that last ran is possible only from a backup made with that older version: database updates only go forward.

## Backups

All of Board Keeper's data lives in the database, in the volume `db-data` (to Docker, `board-keeper_db-data`). The application keeps nothing on disk.

To make a backup while Board Keeper runs:

```bash
docker compose exec -T db pg_dump --username boardkeeper --format custom boardkeeper > board-keeper.dump
```

Keep the file somewhere other than the machine Board Keeper runs on. How often to make one, and where to keep them, is up to you; a backup made by a scheduled job (cron, or your NAS's task scheduler) is the one you'll have when you need it.

To restore a backup, stop the application, load the backup into the database, and start the application again:

```bash
docker compose stop app
```

```bash
docker compose exec -T db pg_restore --username boardkeeper --dbname boardkeeper --clean --if-exists < board-keeper.dump
```

```bash
docker compose start app
```

`--clean` replaces what the database holds with the backup's contents. Everything recorded since the backup is gone.

## When the only Admin has forgotten their password

Any Admin can create a Reset Link for someone else's Account, from that Player's menu on the Players page. When no Admin can sign in, run this on the machine, with the Admin's username:

```bash
docker compose exec app java -jar board-keeper.jar --reset-link-for=USERNAME
```

It prints a one-time link such as `/reset-password/…`. Add it to your address and open it to choose a new password. It works for 24 hours. Board Keeper keeps running meanwhile.

## Letting programs use Board Keeper

A script or an AI agent can use Board Keeper with an API Key, which someone creates on their settings page. It acts as their Account, but never does Admin work. Admins may use API Keys from the start; to let others, give the permission "Use API keys" to one of their Roles. Taking it away stops their API Keys at once, and disabling an Account deletes them. The API is described at `/swagger-ui.html` on your installation.

## Checking that it runs

`https://boardkeeper.example.com/actuator/health` answers with `"status":"UP"` while Board Keeper and its database are fine. A monitoring tool can watch that address.

The logs show what happened, newest last:

```bash
docker compose logs --tail 100 app
```

## If you change Board Keeper's code

Board Keeper is under the GNU Affero General Public License. Running it unchanged asks nothing of you. If you change its code and let others use your copy, the licence asks you to offer them your changed source. The About page, opened from the account menu, links to Board Keeper's own repository; point its links at yours with these environment variables of the `app` container:

| Variable | What it is |
|---|---|
| `BOARDKEEPER_SOURCE_REPOSITORY` | Your repository's page, such as `https://example.org/you/board-keeper`. The page shows the address itself. |
| `BOARDKEEPER_SOURCE_URL` | The code of the version that runs, where `{commit}` stands for the commit your build was made from, such as `https://example.org/you/board-keeper/tree/{commit}`. |
| `BOARDKEEPER_SOURCE_RELEASES` | What is new in a release, where `{version}` stands for the release, such as `https://example.org/you/board-keeper/releases/{version}`. |

Set the first or the last to nothing (`BOARDKEEPER_SOURCE_REPOSITORY=`) and the page leaves that link out.

Your copy calls itself a version, such as 1.2.3, only when it is built from a commit that has that git tag. Any other build is a Snapshot.

## Settings

| In `.env` | What it is |
|---|---|
| `POSTGRES_PASSWORD` | The database's password. Required. |
| `BOARDKEEPER_URL` | The https address people open. Passkeys need it. |
| `BOARD_KEEPER_PORT` | The port on the machine that Board Keeper answers on. 8080 if left out. |
| `BOARD_KEEPER_IMAGE` | The image to run. The newest release (`ghcr.io/oliverjuhasz/board-keeper:latest`) if left out. "Updating" lists the other tags. |

## When something is wrong

- **The sign-in page offers no passkeys.** `BOARDKEEPER_URL` isn't set, or Board Keeper wasn't started again after setting it. If the application doesn't start at all and its log names `BOARDKEEPER_URL`, the address isn't an https one.
- **Passkeys fail.** The address in the browser must be exactly the one in `BOARDKEEPER_URL`.
- **The app can't be installed on a phone.** Phones offer it only over https.
- **The session cookie isn't marked secure, although the address is https.** The proxy isn't sending `X-Forwarded-Proto`.
- **Board Keeper doesn't start, and the log mentions the database.** Check that the `db` container runs (`docker compose ps`) and that `POSTGRES_PASSWORD` hasn't changed since the database was first created: PostgreSQL keeps the password it was created with.
