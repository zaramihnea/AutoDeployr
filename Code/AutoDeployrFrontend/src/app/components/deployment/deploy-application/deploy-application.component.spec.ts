import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DeployApplicationComponent } from './deploy-application.component';

describe('DeployApplicationComponent', () => {
  let component: DeployApplicationComponent;
  let fixture: ComponentFixture<DeployApplicationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeployApplicationComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DeployApplicationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
