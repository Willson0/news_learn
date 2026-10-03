<script>
// Демо-график «свечи». Реальные котировки подключим к бэкенду/провайдеру.
function generateCandles(count, start) {
  const candles = []
  let price = start
  let seed = 42
  const rnd = () => {
    seed = (seed * 9301 + 49297) % 233280
    return seed / 233280
  }
  for (let i = 0; i < count; i += 1) {
    const open = price
    const drift = (rnd() - 0.42) * 70000
    const close = Math.max(3900000, open + drift)
    const high = Math.max(open, close) + rnd() * 40000
    const low = Math.min(open, close) - rnd() * 40000
    candles.push({ open, close, high, low, up: close >= open })
    price = close
  }
  return candles
}

export default {
  name: 'CandlestickChart',
  data() {
    return {
      symbol: 'XAUUSD',
      name: 'Золото / Доллар США',
      price: '4 419,315',
      change: '-54,830 (-1,20%)',
      time: '20:48:42 UTC+3',
      candles: generateCandles(30, 3980000),
      // границы оси цены
      min: 3900000,
      max: 4750000,
      step: 50000,
      width: 340,
      height: 360,
      padRight: 58,
      padBottom: 24,
      current: 4450000,
    }
  },
  computed: {
    plotW() {
      return this.width - this.padRight
    },
    plotH() {
      return this.height - this.padBottom
    },
    gridLines() {
      const lines = []
      for (let v = this.min; v <= this.max; v += this.step) {
        lines.push({ value: v, y: this.y(v), label: this.fmt(v) })
      }
      return lines
    },
    candleGeo() {
      const n = this.candles.length
      const slot = this.plotW / n
      const bw = slot * 0.55
      return this.candles.map((c, i) => {
        const cx = slot * i + slot / 2
        return {
          x: cx - bw / 2,
          cx,
          w: bw,
          up: c.up,
          yHigh: this.y(c.high),
          yLow: this.y(c.low),
          yOpen: this.y(c.open),
          yClose: this.y(c.close),
          bodyY: this.y(Math.max(c.open, c.close)),
          bodyH: Math.max(1, Math.abs(this.y(c.open) - this.y(c.close))),
        }
      })
    },
    currentY() {
      return this.y(this.current)
    },
    months() {
      return [
        { label: 'Июль', x: this.plotW * 0.1 },
        { label: 'Авг', x: this.plotW * 0.45 },
        { label: 'Сен', x: this.plotW * 0.78 },
      ]
    },
  },
  methods: {
    y(value) {
      const t = (value - this.min) / (this.max - this.min)
      return this.plotH - t * this.plotH
    },
    fmt(v) {
      return v.toLocaleString('ru-RU')
    },
  },
}
</script>

<template>
  <div class="chart-card">
    <div class="chart-card__head">
      <p class="chart-card__symbol">{{ symbol }}</p>
      <p class="chart-card__name">
        <span class="chart-card__dot"></span>{{ name }}
      </p>
      <p class="chart-card__price">
        {{ price }} <span class="chart-card__change">{{ change }}</span>
      </p>
      <button type="button" class="chart-card__volume">Объём - Тики ▾</button>
    </div>

    <svg class="chart-card__svg" :viewBox="`0 0 ${width} ${height}`" role="img" aria-label="График котировок">
      <!-- Горизонтальная сетка + подписи цены справа -->
      <g>
        <line
          v-for="g in gridLines"
          :key="'g' + g.value"
          :x1="0"
          :x2="plotW"
          :y1="g.y"
          :y2="g.y"
          stroke="rgba(255,255,255,0.06)"
          stroke-width="1"
        />
        <text
          v-for="g in gridLines"
          :key="'t' + g.value"
          :x="plotW + 6"
          :y="g.y + 3"
          fill="rgba(255,255,255,0.5)"
          font-size="8"
        >{{ g.label }}</text>
      </g>

      <!-- Свечи -->
      <g>
        <template v-for="(c, i) in candleGeo" :key="i">
          <line :x1="c.cx" :x2="c.cx" :y1="c.yHigh" :y2="c.yLow" :stroke="c.up ? '#e9e9ee' : '#8a8a92'" stroke-width="1" />
          <rect :x="c.x" :y="c.bodyY" :width="c.w" :height="c.bodyH" :fill="c.up ? '#e9e9ee' : '#8a8a92'" rx="1" />
        </template>
      </g>

      <!-- Текущая цена (пунктир + метка) -->
      <g>
        <line :x1="0" :x2="plotW" :y1="currentY" :y2="currentY" stroke="rgba(255,255,255,0.35)" stroke-width="1" stroke-dasharray="3 3" />
        <rect :x="plotW" :y="currentY - 9" :width="padRight" height="18" fill="#3a3b40" rx="3" />
        <text :x="plotW + 5" :y="currentY + 3" fill="#fff" font-size="8">{{ fmt(current) }}</text>
      </g>

      <!-- Подписи месяцев -->
      <g>
        <text
          v-for="m in months"
          :key="m.label"
          :x="m.x"
          :y="height - 6"
          fill="rgba(255,255,255,0.6)"
          font-size="9"
        >{{ m.label }}</text>
      </g>
    </svg>

    <div class="chart-card__foot">
      <button type="button" class="chart-card__range">Диапазон дат ▾</button>
      <span class="chart-card__time">{{ time }}</span>
    </div>
  </div>
</template>

<style scoped>
.chart-card {
  padding: 16px;
  border-radius: var(--radius-card);
  background: var(--color-surface);
}

.chart-card__head {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
}
.chart-card__symbol {
  font-size: var(--font-size-md);
  font-weight: 700;
}
.chart-card__name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
}
.chart-card__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #d9d9de;
}
.chart-card__price {
  font-size: var(--font-size-base);
  font-weight: 600;
}
.chart-card__change {
  color: #ff5a5a;
  font-weight: 400;
}
.chart-card__volume {
  align-self: flex-start;
  margin-top: 4px;
  padding: 6px 10px;
  border-radius: 10px;
  background: var(--color-surface-muted);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.chart-card__svg {
  width: 100%;
  height: auto;
  display: block;
}

.chart-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.chart-card__range {
  color: var(--color-text);
  font-size: var(--font-size-base);
}
.chart-card__time {
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
}
</style>
