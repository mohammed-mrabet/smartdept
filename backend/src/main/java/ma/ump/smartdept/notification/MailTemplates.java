package ma.ump.smartdept.notification;

import org.springframework.web.util.HtmlUtils;

/** Gabarit HTML commun à tous les e-mails (notifications, confirmation d'adresse, mot de passe, contact). */
public final class MailTemplates {

    private MailTemplates() {
    }

    public static String html(String firstName, String title, String message, String buttonLabel, String buttonUrl, String footer) {
        String esc = HtmlUtils.htmlEscape(message).replace("\n", "<br>");
        String button = buttonUrl == null ? "" : """
                <p style="margin:24px 0"><a href="%s" style="background:#2563eb;color:#fff;padding:11px 20px;border-radius:8px;text-decoration:none;font-weight:bold">%s</a></p>
                <p style="font-size:12px;color:#6b7280">Si le bouton ne fonctionne pas, copiez ce lien : %s</p>
                """.formatted(buttonUrl, HtmlUtils.htmlEscape(buttonLabel), buttonUrl);
        return """
                <div style="font-family:Arial,sans-serif;background:#f4f6fb;padding:24px">
                  <div style="max-width:560px;margin:auto;background:#fff;border-radius:12px;overflow:hidden;border:1px solid #e3e8f0">
                    <div style="background:linear-gradient(135deg,#0f1b3d,#1d3a8a);color:#fff;padding:16px 24px;font-size:18px;font-weight:bold">SmartDept · FSO Oujda</div>
                    <div style="padding:24px;color:#1f2937;font-size:15px;line-height:1.55">
                      <p>Bonjour %s,</p>
                      <h2 style="font-size:18px;color:#1e3a8a;margin:16px 0 8px">%s</h2>
                      <p>%s</p>
                      %s
                    </div>
                    <div style="padding:12px 24px;background:#f9fafb;color:#6b7280;font-size:12px">%s</div>
                  </div>
                </div>
                """.formatted(HtmlUtils.htmlEscape(firstName == null ? "" : firstName), HtmlUtils.htmlEscape(title), esc, button,
                HtmlUtils.htmlEscape(footer));
    }

    public static String text(String firstName, String message, String url) {
        return "Bonjour " + (firstName == null ? "" : firstName) + ",\n\n" + message + (url == null ? "" : "\n\n" + url) + "\n\n— SmartDept";
    }
}
