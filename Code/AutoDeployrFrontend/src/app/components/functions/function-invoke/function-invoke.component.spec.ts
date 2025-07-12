import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FunctionInvokeComponent } from './function-invoke.component';

describe('FunctionInvokeComponent', () => {
  let component: FunctionInvokeComponent;
  let fixture: ComponentFixture<FunctionInvokeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FunctionInvokeComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(FunctionInvokeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
