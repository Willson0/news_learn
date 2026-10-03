/*
 * Сопоставление ключа инструмента с иконкой материала для карточек отчётов.
 */
import { markRaw } from 'vue'
import IconGold from '@/components/icons/materials/IconGold.vue'
import IconOil from '@/components/icons/materials/IconOil.vue'
import IconUsd from '@/components/icons/materials/IconUsd.vue'
import IconEur from '@/components/icons/materials/IconEur.vue'
import IconGas from '@/components/icons/materials/IconGas.vue'
import IconBtc from '@/components/icons/materials/IconBtc.vue'

const MAP = {
  gold: IconGold,
  silver: IconGold,
  platinum: IconGold,
  wti: IconOil,
  brent: IconOil,
  usd: IconUsd,
  eur: IconEur,
  eurusd: IconEur,
  gas: IconGas,
  btc: IconBtc,
}

export function materialIcon(key) {
  return markRaw(MAP[key] || IconOil)
}

/** Превращает отчёт из API в объект, который ждёт ReportCard. */
export function decorateReport(report) {
  return {
    ...report,
    icon: materialIcon(report.material),
  }
}
