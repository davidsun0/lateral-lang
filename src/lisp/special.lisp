(defun str-concat (:rest lst)
  ; StringBuilder sb = new StringBuilder();
  ; while(!lst.isEmpty()) {
  ;     sb.append(lst.first());
  ; }
  ; return sb.toString();
  (asm (:new "java/lang/StringBuilder")
       :dup
       (:invokespecial "java/lang/StringBuilder" "<init>" "()V")
       (de-asm lst)
       (:checkcast "io/github/whetfire/lateral/Sequence")
       (:goto testlab)
       (:label looplab)
       :dup2
       (:invokevirtual "io/github/whetfire/lateral/Sequence" "first" "()Ljava/lang/Object;")
       (:invokevirtual "java/lang/StringBuilder" "append"
                       "(Ljava/lang/Object;)Ljava/lang/StringBuilder;")
       :pop
       (:invokevirtual "io/github/whetfire/lateral/Sequence"
                       "rest" "()Lio/github/whetfire/lateral/Sequence;")
       (:label testlab)
       :dup
       (:invokevirtual "io/github/whetfire/lateral/Sequence"
                       "isEmpty" "()Z")
       (:ifeq looplab)
       :pop
       (:invokevirtual "java/lang/Object" "toString" "()Ljava/lang/String;")))

(str-concat "L" "java/lang/String" ";")

;; alternative asm synatax
; make it work like quote-unquote
; perhaps rename asm to asm-quote ?

;(defun prep (a b)
;  (asm ,b
;       (:checkcast Sequence)
;       ,a
;       (:invokevirtual Sequence.cons(Object))))

;(defun cons (a b)
;  (prep a b))
