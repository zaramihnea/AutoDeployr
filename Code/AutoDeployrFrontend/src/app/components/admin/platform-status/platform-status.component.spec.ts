import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PlatformStatusComponent } from './platform-status.component';

describe('PlatformStatusComponent', () => {
  let component: PlatformStatusComponent;
  let fixture: ComponentFixture<PlatformStatusComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PlatformStatusComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PlatformStatusComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
