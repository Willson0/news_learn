<template>
    <div v-if="lastPage > 1" class="pager">
        <button class="btn btn-secondary" :disabled="page <= 1" @click="$emit('change', page - 1)">Назад</button>
        <span class="muted">{{ page }} из {{ lastPage }}</span>
        <button class="btn btn-secondary" :disabled="page >= lastPage" @click="$emit('change', page + 1)">Дальше</button>
    </div>
</template>

<script>
export default {
    name: 'Pager',
    props: {
        page: { type: Number, required: true },
        lastPage: { type: Number, required: true },
    },
    emits: ['change'],
};
</script>
