<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

/**
 * Заголовки безопасности для всех ответов. Страницы веб-админки дополнительно получают строгий CSP:
 * скрипты только с нашего домена, без встраивания в чужие страницы.
 */
class SecurityHeaders
{
    public function handle(Request $request, Closure $next): Response
    {
        /** @var Response $response */
        $response = $next($request);
        $headers = $response->headers;

        $headers->set('X-Content-Type-Options', 'nosniff');
        $headers->set('Referrer-Policy', 'strict-origin-when-cross-origin');
        $headers->set('Cross-Origin-Opener-Policy', 'same-origin');

        if ($request->isSecure()) {
            $headers->set('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
        }

        // Ответы со своим CSP (html-отчёты в песочнице) не трогаем: их встраивают в приложения.
        if (! $headers->has('Content-Security-Policy')) {
            $headers->set('X-Frame-Options', 'DENY');

            if ($request->is('admin') || $request->is('admin/*')) {
                $headers->set('Content-Security-Policy', implode('; ', [
                    "default-src 'self'",
                    "script-src 'self'",
                    "style-src 'self' 'unsafe-inline' https://fonts.bunny.net",
                    "font-src 'self' data: https://fonts.bunny.net",
                    "img-src 'self' data: blob: https:",
                    "media-src 'self' blob:",
                    "connect-src 'self'",
                    "frame-src 'self'",
                    "object-src 'none'",
                    "base-uri 'self'",
                    "form-action 'self'",
                    "frame-ancestors 'none'",
                ]));
                $headers->set('Permissions-Policy', 'camera=(), microphone=(), geolocation=(), payment=()');
                $headers->set('Cache-Control', $headers->get('Cache-Control') ?: 'no-store');
            }
        }

        return $response;
    }
}
