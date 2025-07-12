import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FunctionListComponent } from './function-list.component';

describe('FunctionListComponent', () => {
  let component: FunctionListComponent;
  let fixture: ComponentFixture<FunctionListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FunctionListComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(FunctionListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
