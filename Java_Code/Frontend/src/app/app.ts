import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Root component; the shell and every screen are reached through the router. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {}
