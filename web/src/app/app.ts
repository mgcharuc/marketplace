import { Component } from '@angular/core';
import { OrderSummary } from './order-summary/order-summary';

@Component({
  selector: 'app-root',
  imports: [OrderSummary],
  template: `<app-order-summary />`,
})
export class App {}
