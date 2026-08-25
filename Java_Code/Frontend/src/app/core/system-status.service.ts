import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, switchMap, timer } from 'rxjs';

import { environment } from '../../environments/environment';

/** Reachability of the backend, as reported by the actuator health probe. */
export type ServiceStatus = 'checking' | 'online' | 'offline';

/**
 * Polls the backend health endpoint so the shell can show whether the service is reachable.
 *
 * <p>Worth surfacing in a servicing application: the screens are useless without the backend, and a
 * failed call would otherwise only show up as a message on whichever screen the user happened to be
 * on. The probe is unauthenticated and returns a single status field, so polling it is cheap.</p>
 */
@Injectable({ providedIn: 'root' })
export class SystemStatusService {
  private readonly http = inject(HttpClient);

  /** How often the probe repeats, in milliseconds. */
  private static readonly INTERVAL = 60_000;

  readonly status = toSignal(
    timer(0, SystemStatusService.INTERVAL).pipe(
      switchMap(() =>
        this.http.get<{ status: string }>(environment.healthUrl).pipe(
          map((response): ServiceStatus => (response.status === 'UP' ? 'online' : 'offline')),
          // Kept inside the inner observable so a failed probe does not end the polling stream.
          catchError(() => of<ServiceStatus>('offline')),
        ),
      ),
    ),
    { initialValue: 'checking' as ServiceStatus },
  );

  readonly label = computed(() => {
    switch (this.status()) {
      case 'online':
        return 'Service online';
      case 'offline':
        return 'Service unreachable';
      default:
        return 'Checking service';
    }
  });
}
