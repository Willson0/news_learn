<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{{ config('app.name') }}</title>
</head>
<body style="margin:0;padding:0;background:#f4f4f5;font-family:Arial,Helvetica,sans-serif;color:#1a1b1d;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:24px 0;">
        <tr>
            <td align="center">
                <table role="presentation" width="440" cellpadding="0" cellspacing="0" style="max-width:440px;width:100%;background:#ffffff;border-radius:16px;padding:32px;">
                    <tr>
                        <td style="font-size:18px;font-weight:bold;padding-bottom:8px;">
                            {{ config('app.name') }}
                        </td>
                    </tr>
                    <tr>
                        <td style="font-size:15px;line-height:1.5;color:#444;padding-bottom:20px;">
                            @if ($purpose === 'recovery')
                                Код для восстановления пароля:
                            @else
                                Код для подтверждения регистрации:
                            @endif
                        </td>
                    </tr>
                    <tr>
                        <td align="center" style="padding-bottom:20px;">
                            <div style="display:inline-block;font-size:34px;font-weight:bold;letter-spacing:10px;background:#f0f0f1;border-radius:12px;padding:16px 28px;color:#1a1b1d;">
                                {{ $code }}
                            </div>
                        </td>
                    </tr>
                    <tr>
                        <td style="font-size:13px;line-height:1.5;color:#888;">
                            Код действует 15 минут. Если вы не запрашивали его, просто проигнорируйте это письмо.
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
</body>
</html>
