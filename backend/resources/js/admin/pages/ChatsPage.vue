<template>
    <section class="chats">
        <aside class="chat-list">
            <div class="chips">
                <button class="btn" :class="tab === 'direct' ? 'btn-primary' : 'btn-secondary'" @click="switchTab('direct')">Личные</button>
                <button class="btn" :class="tab === 'groups' ? 'btn-primary' : 'btn-secondary'" @click="switchTab('groups')">Общие</button>
            </div>
            <ul class="list selectable">
                <li v-for="c in chats" :key="c.slug" :class="{ selected: c.slug === slug }" @click="open(c.slug)">
                    <div class="strong">{{ c.title }}</div>
                    <div class="muted small">{{ c.preview || c.username || (c.messages + ' сообщ.') }}</div>
                </li>
                <li v-if="chats.length === 0" class="muted">Чатов пока нет</li>
            </ul>
        </aside>

        <div class="chat-view">
            <ErrorText :message="error" />
            <template v-if="slug">
                <header class="chat-head"><h2>{{ title }}</h2></header>
                <div ref="log" class="chat-log">
                    <div v-for="m in messages" :key="m.id" class="msg" :class="{ admin: m.admin, system: m.system }">
                        <div class="msg-meta small muted">
                            {{ m.author }} · {{ m.time }}<span v-if="m.private"> · лично</span>
                        </div>
                        <div v-if="m.reply" class="msg-reply small">
                            <span class="strong">{{ m.reply.author }}</span>
                            <span class="muted"> {{ m.reply.text }}</span>
                        </div>
                        <div v-if="images(m).length" class="msg-images" :class="{ multi: images(m).length > 1 }">
                            <a v-for="a in images(m)" :key="a.id" :href="a.url" target="_blank" rel="noopener">
                                <img :src="a.url" :alt="a.name || 'Фото'" loading="lazy">
                            </a>
                        </div>
                        <div v-for="a in voices(m)" :key="a.id" class="msg-voice">
                            <audio :src="a.url" controls preload="none" />
                            <span v-if="a.duration" class="small muted">{{ a.duration }}</span>
                        </div>
                        <a v-for="a in files(m)" :key="a.id" class="msg-file" :href="a.url" target="_blank" rel="noopener">
                            📎 {{ a.name || 'Файл' }}<span v-if="a.size" class="small muted"> · {{ a.size }}</span>
                        </a>
                        <div v-if="m.text" class="msg-text">{{ m.text }}</div>
                    </div>
                </div>
                <form class="reply" @submit.prevent="send">
                    <textarea v-model="draft" class="input" rows="2" maxlength="4000" placeholder="Ответ от администратора" />
                    <button class="btn btn-primary" :disabled="!draft.trim() || busy">Отправить</button>
                </form>
            </template>
            <p v-else class="muted">Выберите чат слева</p>
        </div>
    </section>
</template>

<script>
import { api } from '../api';
import ErrorText from '../components/ErrorText.vue';

export default {
    name: 'ChatsPage',
    components: { ErrorText },
    data() {
        return {
            tab: 'direct',
            chats: [],
            slug: null,
            title: '',
            messages: [],
            draft: '',
            busy: false,
            error: '',
            timer: null,
        };
    },
    mounted() {
        this.loadList();
        this.timer = setInterval(() => {
            if (this.slug) this.loadMessages(false);
        }, 15000);
    },
    beforeUnmount() {
        clearInterval(this.timer);
    },
    methods: {
        images(m) {
            return (m.attachments || []).filter((a) => a.kind === 'image');
        },
        voices(m) {
            return (m.attachments || []).filter((a) => a.kind === 'voice');
        },
        files(m) {
            return (m.attachments || []).filter((a) => a.kind !== 'image' && a.kind !== 'voice');
        },
        async loadList() {
            try {
                const path = this.tab === 'direct' ? '/chats/direct' : '/chats/groups';
                this.chats = (await api('GET', path)).data;
            } catch (e) {
                this.error = e.message;
            }
        },
        switchTab(tab) {
            this.tab = tab;
            this.slug = null;
            this.messages = [];
            this.loadList();
        },
        open(slug) {
            this.slug = slug;
            this.loadMessages(true);
        },
        async loadMessages(scroll) {
            try {
                const res = await api('GET', `/chats/${this.slug}/messages`);
                this.title = res.chat.title;
                this.messages = res.data;
                if (scroll) this.$nextTick(() => this.scrollDown());
            } catch (e) {
                this.error = e.message;
            }
        },
        async send() {
            this.busy = true;
            this.error = '';
            try {
                await api('POST', `/chats/${this.slug}/messages`, { body: this.draft.trim() });
                this.draft = '';
                await this.loadMessages(true);
            } catch (e) {
                this.error = e.message;
            } finally {
                this.busy = false;
            }
        },
        scrollDown() {
            const log = this.$refs.log;
            if (log) log.scrollTop = log.scrollHeight;
        },
    },
};
</script>
