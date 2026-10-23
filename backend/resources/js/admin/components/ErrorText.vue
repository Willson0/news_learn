<template>
    <p v-if="message" class="error" role="alert">{{ message }}</p>
</template>

<script>
export default {
    name: 'ErrorText',
    props: {
        message: { type: String, default: '' },
    },
};
</script>
