import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MainLayoutComponent } from './components/layout/main-layout/main-layout.component';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, MainLayoutComponent],
  template: `
    <app-main-layout></app-main-layout>
  `,
  styles: []
})
export class AppComponent implements OnInit {
  title = 'Serverless Platform';

  constructor(private authService: AuthService) {}

  ngOnInit(): void {
    // Check token on application start
    this.authService.currentUser$.subscribe();
  }
}