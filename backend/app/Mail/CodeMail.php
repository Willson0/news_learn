<?php

namespace App\Mail;

use Illuminate\Bus\Queueable;
use Illuminate\Mail\Mailable;
use Illuminate\Mail\Mailables\Content;
use Illuminate\Mail\Mailables\Envelope;
use Illuminate\Queue\SerializesModels;

class CodeMail extends Mailable
{
    use Queueable, SerializesModels;

    /**
     * @param  string  $code  Код подтверждения
     * @param  string  $purpose  'register' | 'recovery'
     */
    public function __construct(
        public string $code,
        public string $purpose = 'register',
    ) {}

    public function envelope(): Envelope
    {
        $subject = $this->purpose === 'recovery'
            ? 'Код восстановления пароля'
            : 'Код подтверждения регистрации';

        return new Envelope(subject: $subject);
    }

    public function content(): Content
    {
        return new Content(
            view: 'mail.code',
            with: [
                'code' => $this->code,
                'purpose' => $this->purpose,
            ],
        );
    }
}
