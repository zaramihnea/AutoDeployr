import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FunctionDetailComponent } from './function-detail.component';

describe('FunctionDetailComponent', () => {
  let component: FunctionDetailComponent;
  let fixture: ComponentFixture<FunctionDetailComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FunctionDetailComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(FunctionDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
