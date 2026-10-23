<template>
    <div class="overlay" @click.self="$emit('close')">
        <div class="modal" role="dialog" :aria-label="title">
            <header class="modal-head">
                <h2>{{ title }}</h2>
                <button class="btn btn-ghost" aria-label="Закрыть" @click="$emit('close')">×</button>
            </header>
            <slot />
        </div>
    </div>
</template>

<script>
export default {
    name: 'Modal',
    props: {
        title: { type: String, required: true },
    },
    emits: ['close'],
};
</script>
