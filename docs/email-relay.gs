/**
 * RMShop email relay: sends password-reset emails from your own Gmail.
 *
 * The RMShop backend POSTs {secret, to, subject, text, html} to this web app,
 * and it sends the email with MailApp, so it arrives from your real Gmail
 * address. Free Gmail accounts can send about 100 emails a day this way.
 *
 * Setup (script.google.com):
 *   1. Paste this file into Code.gs and save.
 *   2. Choose "setup" in the function menu and click Run. Allow the permissions.
 *      The Execution log shows the secret to put on Render as EMAIL_RELAY_SECRET.
 *   3. Deploy > New deployment > Web app. Execute as: Me. Who has access: Anyone.
 *      Put the Web app URL (ends in /exec) on Render as EMAIL_RELAY_URL.
 */

function doPost(e) {
  var secret = PropertiesService.getScriptProperties().getProperty('RMSHOP_SECRET');
  var body;
  try {
    body = JSON.parse(e.postData.contents);
  } catch (err) {
    return reply({ ok: false, error: 'Request was not valid JSON' });
  }

  // Anyone could find this URL; only the RMShop server knows the secret
  if (!secret || body.secret !== secret) {
    return reply({ ok: false, error: 'Wrong or missing secret' });
  }
  if (!body.to || !body.subject || !body.text) {
    return reply({ ok: false, error: 'Missing to, subject or text' });
  }

  try {
    MailApp.sendEmail({
      to: body.to,
      subject: body.subject,
      body: body.text,
      htmlBody: body.html || undefined,
      name: 'RMShop'
    });
  } catch (err) {
    return reply({ ok: false, error: String(err) });
  }
  return reply({ ok: true, remainingToday: MailApp.getRemainingDailyQuota() });
}

// Run once by hand. Creates the shared secret (or shows the existing one)
// and asks for permission to send email on your behalf.
function setup() {
  var props = PropertiesService.getScriptProperties();
  var secret = props.getProperty('RMSHOP_SECRET');
  if (!secret) {
    secret = Utilities.getUuid().replace(/-/g, '') + Utilities.getUuid().replace(/-/g, '');
    props.setProperty('RMSHOP_SECRET', secret);
  }
  Logger.log('Emails you can still send today: ' + MailApp.getRemainingDailyQuota());
  Logger.log('EMAIL_RELAY_SECRET for Render: ' + secret);
}

function reply(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
