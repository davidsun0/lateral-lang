(defun nil? (p)
  (if p nil t))

(defun not (p)
  (nil? p))

(defun last (lst)
  (if (cdr lst)
    (last (cdr lst))
    (car lst)))

;(defmacro mclass (name)
;  (class name))

; a and b are top of stack
; ..., a, b ->
; ..., (cons a b)
(defasm cons (a b)
  (let (endlab (makeLabel))
    (:dup ; dup b
     (:ifnull endlab)
     :dup
     (:instanceof LinkedList)
     (:iftrue endlab)
     ; somehow load the exception
     :athrow
     (:label endlab)
     (:new LinkedList)
     (:invokevirtual <init>))))

(defun main ()
  (car (cons :hello nil)))
